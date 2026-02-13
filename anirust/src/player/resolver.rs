use anyhow::Result;
use async_trait::async_trait;
use url::Url;

use crate::domain::PlayerKind;

use super::kodik::resolve_kodik;
use super::ResolvedMedia;

#[async_trait]
pub trait PlayerResolver: Send + Sync {
    fn kind(&self) -> PlayerKind;
    fn label(&self) -> &'static str;
    fn supported(&self) -> bool {
        true
    }
    async fn resolve(&self, url: &Url) -> Result<ResolvedMedia>;
}

struct KodikResolver;
struct DirectResolver;

struct UnsupportedResolver {
    kind: PlayerKind,
    label: &'static str,
}

static KODIK_RESOLVER: KodikResolver = KodikResolver;
static DIRECT_RESOLVER: DirectResolver = DirectResolver;
static ALLOHA_RESOLVER: UnsupportedResolver = UnsupportedResolver {
    kind: PlayerKind::Alloha,
    label: "Alloha",
};
static UNKNOWN_RESOLVER: UnsupportedResolver = UnsupportedResolver {
    kind: PlayerKind::Unknown,
    label: "Unknown",
};

struct ResolverEntry {
    kind: PlayerKind,
    order: u8,
    resolver: &'static dyn PlayerResolver,
}

static RESOLVERS: [ResolverEntry; 4] = [
    ResolverEntry {
        kind: PlayerKind::Kodik,
        order: 1,
        resolver: &KODIK_RESOLVER,
    },
    ResolverEntry {
        kind: PlayerKind::Direct,
        order: 2,
        resolver: &DIRECT_RESOLVER,
    },
    ResolverEntry {
        kind: PlayerKind::Alloha,
        order: 3,
        resolver: &ALLOHA_RESOLVER,
    },
    ResolverEntry {
        kind: PlayerKind::Unknown,
        order: 9,
        resolver: &UNKNOWN_RESOLVER,
    },
];

pub fn player_label(kind: PlayerKind) -> &'static str {
    resolver_entry(kind).resolver.label()
}

pub fn player_order(kind: PlayerKind) -> u8 {
    resolver_entry(kind).order
}

pub fn is_supported_kind(kind: PlayerKind) -> bool {
    resolver_entry(kind).resolver.supported()
}

pub(crate) async fn resolve_with_kind(
    kind: PlayerKind,
    url: &Url,
) -> Result<ResolvedMedia> {
    resolver_entry(kind).resolver.resolve(url).await
}

fn resolver_entry(kind: PlayerKind) -> &'static ResolverEntry {
    RESOLVERS
        .iter()
        .find(|entry| entry.kind == kind)
        .unwrap_or_else(|| &RESOLVERS[RESOLVERS.len() - 1])
}

#[async_trait]
impl PlayerResolver for KodikResolver {
    fn kind(&self) -> PlayerKind {
        PlayerKind::Kodik
    }

    fn label(&self) -> &'static str {
        "Kodik"
    }

    async fn resolve(&self, url: &Url) -> Result<ResolvedMedia> {
        resolve_kodik(url).await
    }
}

#[async_trait]
impl PlayerResolver for DirectResolver {
    fn kind(&self) -> PlayerKind {
        PlayerKind::Direct
    }

    fn label(&self) -> &'static str {
        "Direct"
    }

    async fn resolve(&self, url: &Url) -> Result<ResolvedMedia> {
        if PlayerKind::from_url(url) != PlayerKind::Direct {
            return Err(anyhow::anyhow!("direct resolver received non-media url"));
        }

        Ok(ResolvedMedia {
            url: url.as_str().to_string(),
            headers: Vec::new(),
        })
    }
}

#[async_trait]
impl PlayerResolver for UnsupportedResolver {
    fn kind(&self) -> PlayerKind {
        self.kind
    }

    fn label(&self) -> &'static str {
        self.label
    }

    fn supported(&self) -> bool {
        false
    }

    async fn resolve(&self, url: &Url) -> Result<ResolvedMedia> {
        let host = url.host_str().unwrap_or("<unknown>");
        if self.kind == PlayerKind::Alloha {
            return Err(anyhow::anyhow!(
                "unsupported player: alloha (host: {}). Try a different dubbing or provider.",
                host
            ));
        }

        Err(anyhow::anyhow!(
            "unsupported player host: {} ({}).",
            host,
            self.label
        ))
    }
}
