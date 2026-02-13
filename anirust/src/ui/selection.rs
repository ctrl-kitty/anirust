use ratatui::widgets::ListState;

pub(crate) fn select_first(len: usize) -> Option<usize> {
    if len == 0 {
        None
    } else {
        Some(0)
    }
}

pub(crate) fn select_next(state: &mut ListState, len: usize) {
    if len == 0 {
        state.select(None);
        return;
    }

    let next = match state.selected() {
        Some(index) if index + 1 < len => index + 1,
        Some(index) => index,
        None => 0,
    };
    state.select(Some(next));
}

pub(crate) fn select_prev(state: &mut ListState, len: usize) {
    if len == 0 {
        state.select(None);
        return;
    }

    let prev = match state.selected() {
        Some(index) if index > 0 => index - 1,
        Some(index) => index,
        None => 0,
    };
    state.select(Some(prev));
}
