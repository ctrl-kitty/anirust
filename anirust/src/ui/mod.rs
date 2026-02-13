mod app;
mod app_view;
mod handlers;
mod handlers_flow;
mod input;
mod playback;
mod render;
mod selection;

#[cfg(test)]
mod app_tests;

#[cfg(test)]
mod handlers_tests;


use anyhow::{Context, Result};
use crossterm::event::{self, Event, KeyEventKind};
use crossterm::execute;
use crossterm::terminal::{disable_raw_mode, enable_raw_mode, EnterAlternateScreen, LeaveAlternateScreen};
use ratatui::backend::{Backend, CrosstermBackend};
use ratatui::Terminal;
use std::io;
use std::time::Duration;

use crate::domain::ProviderId;
use crate::registry::ProviderRegistry;
use crate::settings;

use app::App;

pub async fn run() -> Result<()> {
    settings::ensure_config()?;
    let settings = settings::Settings::load()?;
    let registry = ProviderRegistry::load();
    let provider_id = ProviderId::new(settings.preferred_provider.clone());

    let mut app = App::new(registry, provider_id, settings);

    enable_raw_mode().context("enable raw mode")?;
    let mut stdout = io::stdout();
    execute!(stdout, EnterAlternateScreen).context("enter alternate screen")?;
    let backend = CrosstermBackend::new(stdout);
    let mut terminal = Terminal::new(backend).context("create terminal")?;

    let result = run_app(&mut terminal, &mut app).await;

    disable_raw_mode().context("disable raw mode")?;
    execute!(terminal.backend_mut(), LeaveAlternateScreen)
        .context("leave alternate screen")?;
    terminal.show_cursor().context("show cursor")?;

    result
}

async fn run_app<B: Backend + io::Write>(terminal: &mut Terminal<B>, app: &mut App) -> Result<()> {
    loop {
        terminal.draw(|frame| render::draw_ui(frame, app))?;

        if event::poll(Duration::from_millis(100)).context("poll for events")? {
            if let Event::Key(key) = event::read().context("read event")? {
                if key.kind != KeyEventKind::Press {
                    continue;
                }

                if handlers::handle_key(app, key).await? {
                    return Ok(());
                }
            }
        }

        if let Some(request) = app.take_play_request() {
            playback::handle_playback(terminal, app, request).await?;
        }
    }
}
