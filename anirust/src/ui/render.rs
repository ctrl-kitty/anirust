use ratatui::layout::{Constraint, Direction, Layout};
use ratatui::style::{Color, Modifier, Style};
use ratatui::widgets::{Block, Borders, List, Paragraph, Wrap};
use ratatui::Frame;

use super::app::{App, View};

pub(crate) fn draw_ui(frame: &mut Frame<'_>, app: &mut App) {
    let chunks = Layout::default()
        .direction(Direction::Vertical)
        .constraints([
            Constraint::Length(3),
            Constraint::Min(1),
            Constraint::Length(5),
        ])
        .split(frame.size());

    let (header_title, header_text, cursor_offset, header_style) = app.header(chunks[0].width);
    let header = Paragraph::new(header_text)
        .block(Block::default().borders(Borders::ALL).title(header_title))
        .style(header_style);
    frame.render_widget(header, chunks[0]);

    let list_title = match app.view {
        View::Search => "Results",
        View::Series => "Series",
        View::Dubbing => "Dubbing",
        View::SaveDubbing => "Save Default",
        View::Player => "Player",
        View::Episodes => "Episodes",
    };
    let list_items = app.list_items();
    let list = List::new(list_items)
        .block(Block::default().borders(Borders::ALL).title(list_title))
        .highlight_style(
            Style::default()
                .fg(Color::Yellow)
                .add_modifier(Modifier::BOLD),
        )
        .highlight_symbol("> ");

    match app.view {
        View::Search => frame.render_stateful_widget(list, chunks[1], &mut app.results_state),
        View::Series => frame.render_stateful_widget(list, chunks[1], &mut app.series_state),
        View::Dubbing => frame.render_stateful_widget(list, chunks[1], &mut app.dubbing_state),
        View::SaveDubbing => frame.render_stateful_widget(list, chunks[1], &mut app.save_state),
        View::Player => frame.render_stateful_widget(list, chunks[1], &mut app.player_state),
        View::Episodes => frame.render_stateful_widget(list, chunks[1], &mut app.episodes_state),
    }

    let footer = Paragraph::new(app.footer_text())
        .block(Block::default().borders(Borders::ALL).title("Status"))
        .wrap(Wrap { trim: false });
    frame.render_widget(footer, chunks[2]);

    if let Some(offset) = cursor_offset {
        let cursor_x = chunks[0].x + 1 + offset;
        let cursor_y = chunks[0].y + 1;
        frame.set_cursor(cursor_x, cursor_y);
    }
}
