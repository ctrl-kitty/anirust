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

pub fn player_label(kind: PlayerKind) -> &'static str {
    resolver_for(kind).label()
}

pub fn player_order(kind: PlayerKind) -> u8 {
    match kind {
        PlayerKind::Kodik => 1,
        PlayerKind::Direct => 2,
        PlayerKind::Alloha => 3,
        PlayerKind::Unknown => 9,
    }
}

pub fn is_supported_kind(kind: PlayerKind) -> bool {
    resolver_for(kind).supported()
}

pub(crate) async fn resolve_with_kind(
    kind: PlayerKind,
    url: &Url,
) -> Result<ResolvedMedia> {
    resolver_for(kind).resolve(url).await
}

fn resolver_for(kind: PlayerKind) -> &'static dyn PlayerResolver {
    match kind {
        PlayerKind::Kodik => &KODIK_RESOLVER,
        PlayerKind::Direct => &DIRECT_RESOLVER,
        PlayerKind::Alloha => &ALLOHA_RESOLVER,
        PlayerKind::Unknown => &UNKNOWN_RESOLVER,
    }
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
