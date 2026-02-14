use anyhow::{Context, Result};
use clap::{Parser, Subcommand};
use std::path::PathBuf;

use anirust::domain::{AnimeId, ProviderError, ProviderId, ProviderStatus};
use anirust::formatting::format_id;
use anirust::registry::ProviderRegistry;
use anirust::services::catalog::CatalogService;
use anirust::settings;
use anirust::ui;

#[derive(Parser, Debug)]
#[command(name = "anirust", version, about = "Anime TUI player")]
struct Cli {
    #[command(subcommand)]
    command: Option<Command>,
    #[arg(long, global = true, value_name = "PATH")]
    log_file: Option<PathBuf>,
}

#[derive(Subcommand, Debug)]
enum Command {
    Settings,
    Tui,
    Providers,
    Search {
        query: String,
        #[arg(long)]
        provider: Option<String>,
    },
    Series {
        anime_id: u64,
        #[arg(long)]
        provider: Option<String>,
    },
    Episodes {
        anime_id: u64,
        #[arg(long)]
        provider: Option<String>,
    },
}

#[tokio::main]
async fn main() -> Result<()> {
    let Cli { command, log_file } = Cli::parse();
    init_logging(log_file)?;

    match command {
        Some(Command::Settings) => {
            let state = settings::ensure_config()?;
            println!("Config path: {}", state.path.display());
            if state.created {
                println!("Default config created.");
            } else {
                println!("Config already exists.");
            }
        }
        Some(Command::Providers) => list_providers(),
        Some(Command::Search { query, provider }) => run_search(query, provider).await?,
        Some(Command::Series { anime_id, provider }) => run_series(anime_id, provider).await?,
        Some(Command::Episodes { anime_id, provider }) => run_episodes(anime_id, provider).await?,
        Some(Command::Tui) | None => ui::run().await?,
    }

    Ok(())
}

fn init_logging(path: Option<PathBuf>) -> Result<()> {
    let Some(path) = path else {
        return Ok(());
    };

    let file = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(&path)
        .with_context(|| format!("open log file {}", path.display()))?;
    let subscriber = tracing_subscriber::fmt()
        .with_writer(file)
        .with_ansi(false)
        .with_max_level(tracing::Level::DEBUG)
        .finish();
    tracing::subscriber::set_global_default(subscriber)
        .context("set global logger")?;
    Ok(())
}

fn list_providers() {
    let registry = ProviderRegistry::load();
    println!("Anime providers:");
    for provider in registry.providers() {
        let provider = provider.as_ref();
        let id = provider.id();
        let caps = provider.capabilities();
        println!(
            "- {} (search: {}, series: {}, episodes: {})",
            id, caps.search, caps.series_list, caps.episodes
        );
    }

    if !registry.metadata_providers().is_empty() {
        println!("Metadata providers:");
        for provider in registry.metadata_providers() {
            let provider = provider.as_ref();
            let id = provider.id();
            println!("- {} (search only)", id);
        }
    }
}

async fn run_search(query: String, provider: Option<String>) -> Result<()> {
    let registry = ProviderRegistry::load();
    let filter = provider.map(ProviderId::new);
    let mut matched = false;

    println!("Searching for: {}", query);

    for provider in registry.providers() {
        let provider = provider.as_ref();
        let id = provider.id();
        if let Some(filter) = filter.as_ref() {
            if &id != filter {
                continue;
            }
        }

        matched = true;
        let metadata = if id == ProviderId::from("yummy") {
            registry.get_metadata(&ProviderId::from("shikimori"))
        } else {
            None
        };
        let catalog = CatalogService::new(provider, metadata);
        let result = catalog.search(&query).await;
        print_search_result(&id, result);
    }

    for provider in registry.metadata_providers() {
        let provider = provider.as_ref();
        let id = provider.id();
        if let Some(filter) = filter.as_ref() {
            if &id != filter {
                continue;
            }
        }

        matched = true;
        let result = provider.search(&query).await;
        print_search_result(&id, result);
    }

    if !matched {
        return Err(anyhow::anyhow!(
            "provider not found: {}",
            filter
                .as_ref()
                .map(|id| id.to_string())
                .unwrap_or_else(|| "<none>".to_string())
        ));
    }

    Ok(())
}

async fn run_series(anime_id: u64, provider: Option<String>) -> Result<()> {
    let registry = ProviderRegistry::load();
    let provider_id = resolve_provider_id(provider)?;
    let provider = registry
        .get(&provider_id)
        .ok_or_else(|| anyhow::anyhow!("provider not found: {}", provider_id))?;
    let metadata = if provider_id == ProviderId::from("yummy") {
        registry.get_metadata(&ProviderId::from("shikimori"))
    } else {
        None
    };
    let catalog = CatalogService::new(provider, metadata);

    let anime_id = AnimeId {
        shikimori_id: None,
        mal_id: None,
        yummy_id: Some(anime_id),
    };

    let result = catalog.series(&anime_id).await;
    let anirust::domain::ProviderResult {
        status,
        data,
        error,
    } = result;

    match status {
        ProviderStatus::Ok | ProviderStatus::Partial => {
            let entries = data.unwrap_or_default();
            if status == ProviderStatus::Partial {
                if let Some(error) = error.as_ref() {
                    println!("Warning: {}", error.message);
                }
            }

            println!("Series entries: {}", entries.len());
            for entry in entries {
                let order = entry
                    .order
                    .map(|value| value.to_string())
                    .unwrap_or_else(|| "-".to_string());
                println!("- [{}] {} (id: {})", order, entry.title, entry.id);
            }
        }
        ProviderStatus::NotFound => {
            println!("No series entries found for {}", provider_id);
        }
        ProviderStatus::RateLimited | ProviderStatus::Unauthorized | ProviderStatus::Error => {
            print_provider_error(&provider_id, status, error);
        }
    }

    Ok(())
}

async fn run_episodes(anime_id: u64, provider: Option<String>) -> Result<()> {
    let registry = ProviderRegistry::load();
    let provider_id = resolve_provider_id(provider)?;
    let provider = registry
        .get(&provider_id)
        .ok_or_else(|| anyhow::anyhow!("provider not found: {}", provider_id))?;
    let metadata = if provider_id == ProviderId::from("yummy") {
        registry.get_metadata(&ProviderId::from("shikimori"))
    } else {
        None
    };
    let catalog = CatalogService::new(provider, metadata);

    let result = catalog.episodes(&anime_id.to_string()).await;
    let anirust::domain::ProviderResult {
        status,
        data,
        error,
    } = result;

    match status {
        ProviderStatus::Ok | ProviderStatus::Partial => {
            let episodes = data.unwrap_or_default();
            if status == ProviderStatus::Partial {
                if let Some(error) = error.as_ref() {
                    println!("Warning: {}", error.message);
                }
            }

            println!("Episodes: {}", episodes.len());
            for episode in episodes {
                let number = episode
                    .number
                    .map(|value| value.to_string())
                    .unwrap_or_else(|| "-".to_string());
                let dubbing = episode
                    .voice_variants
                    .iter()
                    .map(|voice| voice.label.as_str())
                    .collect::<Vec<_>>()
                    .join(", ");
                let dubbing = if dubbing.is_empty() { "-" } else { &dubbing };
                let iframe_url = episode
                    .iframe_url
                    .as_ref()
                    .map(|url| url.as_str())
                    .unwrap_or("-");
                println!("- ep {} | dubbing: {} | url: {}", number, dubbing, iframe_url);
            }
        }
        ProviderStatus::NotFound => {
            println!("No episodes found for {}", provider_id);
        }
        ProviderStatus::RateLimited | ProviderStatus::Unauthorized | ProviderStatus::Error => {
            print_provider_error(&provider_id, status, error);
        }
    }

    Ok(())
}

fn resolve_provider_id(provider: Option<String>) -> Result<ProviderId> {
    if let Some(value) = provider {
        return Ok(ProviderId::new(value));
    }

    settings::ensure_config()?;
    let settings = settings::Settings::load()?;
    Ok(ProviderId::new(settings.preferred_provider))
}

fn print_search_result(
    id: &ProviderId,
    result: anirust::domain::ProviderResult<Vec<anirust::domain::Anime>>,
) {
    let anirust::domain::ProviderResult {
        status,
        data,
        error,
    } = result;

    match status {
        ProviderStatus::Ok | ProviderStatus::Partial => {
            let items = data.unwrap_or_default();
            println!("[{}] results: {}", id, items.len());
            if status == ProviderStatus::Partial {
                if let Some(error) = error.as_ref() {
                    println!("[{}] warning: {}", id, error.message);
                }
            }
            for (index, anime) in items.iter().enumerate() {
                println!(
                    "  {}. {} (yummy: {}, shikimori: {}, mal: {})",
                    index + 1,
                    anime.title,
                    format_id(anime.id.yummy_id),
                    format_id(anime.id.shikimori_id),
                    format_id(anime.id.mal_id)
                );
            }
        }
        ProviderStatus::NotFound => {
            println!("[{}] no results", id);
        }
        ProviderStatus::RateLimited | ProviderStatus::Unauthorized | ProviderStatus::Error => {
            print_provider_error(id, status, error);
        }
    }
}

fn print_provider_error(id: &ProviderId, status: ProviderStatus, error: Option<ProviderError>) {
    let message = error
        .map(|error| error.message)
        .unwrap_or_else(|| "unknown error".to_string());
    println!("[{}] {:?}: {}", id, status, message);
}
