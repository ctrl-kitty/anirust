use anyhow::{Context, Result};
use crossterm::execute;
use crossterm::terminal::{disable_raw_mode, enable_raw_mode, EnterAlternateScreen, LeaveAlternateScreen};
use ratatui::backend::Backend;
use ratatui::Terminal;
use std::io;

use crate::player;

use super::app::{App, PlayRequest};

pub(crate) async fn handle_playback<B: Backend + io::Write>(
    terminal: &mut Terminal<B>,
    app: &mut App,
    request: PlayRequest,
) -> Result<()> {
    suspend_terminal(terminal)?;
    let play_result = player::play_with_kind(
        &app.settings,
        request.player_kind,
        &request.url,
        Some(&request.media_title),
    )
    .await;
    resume_terminal(terminal)?;

    match play_result {
        Ok(()) => {
            if let Err(err) = app.update_history(&request) {
                app.set_status(format!("Played, but failed to save history: {}", err));
            } else {
                let label = request
                    .episode_number
                    .map(|value| value.to_string())
                    .unwrap_or_else(|| request.episode_id.clone());
                app.set_status(format!("Played episode {}", label));
            }
        }
        Err(err) => {
            app.set_status(format!("Playback failed: {}", err));
        }
    }

    Ok(())
}

fn suspend_terminal<B: Backend + io::Write>(terminal: &mut Terminal<B>) -> Result<()> {
    disable_raw_mode().context("disable raw mode")?;
    execute!(terminal.backend_mut(), LeaveAlternateScreen)
        .context("leave alternate screen")?;
    terminal.show_cursor().context("show cursor")?;
    Ok(())
}

fn resume_terminal<B: Backend + io::Write>(terminal: &mut Terminal<B>) -> Result<()> {
    enable_raw_mode().context("enable raw mode")?;
    execute!(terminal.backend_mut(), EnterAlternateScreen)
        .context("enter alternate screen")?;
    terminal.clear().context("clear terminal")?;
    Ok(())
}
