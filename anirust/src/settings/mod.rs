use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;
use std::env;
use std::fs;
use std::path::PathBuf;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct Settings {
    pub preferred_provider: String,
    pub yummy: YummySettings,
    pub audio: AudioSettings,
    pub player: PlayerSettings,
    pub anime: AnimeSettings,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct YummySettings {
    pub app_token: String,
    pub lang: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct AudioSettings {
    pub preferred_dubbings: Vec<String>,
    pub preferred_subtitles: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct PlayerSettings {
    pub command: String,
    pub args: Vec<String>,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            preferred_provider: "yummy".to_string(),
            yummy: YummySettings::default(),
            audio: AudioSettings::default(),
            player: PlayerSettings::default(),
            anime: AnimeSettings::default(),
        }
    }
}

impl Default for YummySettings {
    fn default() -> Self {
        Self {
            app_token: String::new(),
            lang: "ru".to_string(),
        }
    }
}

impl Default for AudioSettings {
    fn default() -> Self {
        Self {
            preferred_dubbings: Vec::new(),
            preferred_subtitles: Vec::new(),
        }
    }
}

impl Default for PlayerSettings {
    fn default() -> Self {
        Self {
            command: "mpv".to_string(),
            args: vec!["--force-window=yes".to_string()],
        }
    }
}

impl Settings {
    pub fn load() -> Result<Self> {
        let path = config_path();
        let content = fs::read_to_string(&path)
            .with_context(|| format!("read config from {}", path.display()))?;
        let settings = toml::from_str(&content)
            .with_context(|| format!("parse config from {}", path.display()))?;
        Ok(settings)
    }

    pub fn to_toml(&self) -> Result<String> {
        toml::to_string_pretty(self).context("serialize config to toml")
    }

    pub fn save(&self) -> Result<()> {
        let state = ensure_config()?;
        let toml = self.to_toml()?;
        fs::write(&state.path, toml)
            .with_context(|| format!("write config to {}", state.path.display()))
    }
}

#[cfg(test)]
mod tests;

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(default)]
pub struct AnimeSettings {
    pub defaults: BTreeMap<String, AnimeDefaults>,
    pub history: BTreeMap<String, AnimeHistory>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(default)]
pub struct AnimeDefaults {
    pub dubbing: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(default)]
pub struct AnimeHistory {
    pub last_episode_id: Option<String>,
    pub last_episode_number: Option<u32>,
}

pub struct ConfigState {
    pub path: PathBuf,
    pub created: bool,
}

pub fn config_path() -> PathBuf {
    let mut path = config_dir();
    path.push("anirust");
    path.push("config.toml");
    path
}

pub fn ensure_config() -> Result<ConfigState> {
    let path = config_path();
    if path.exists() {
        return Ok(ConfigState {
            path,
            created: false,
        });
    }

    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)
            .with_context(|| format!("create config directory {}", parent.display()))?;
    }

    let settings = Settings::default();
    let toml = settings.to_toml()?;
    fs::write(&path, toml).with_context(|| format!("write config to {}", path.display()))?;

    Ok(ConfigState {
        path,
        created: true,
    })
}

fn config_dir() -> PathBuf {
    if let Some(dir) = env::var_os("XDG_CONFIG_HOME") {
        return PathBuf::from(dir);
    }

    if let Some(home) = env::var_os("HOME") {
        return PathBuf::from(home).join(".config");
    }

    PathBuf::from(".")
}
