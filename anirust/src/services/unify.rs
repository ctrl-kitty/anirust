use crate::domain::{Anime, AnimeId, ProviderError, ProviderResult, ProviderStatus};
use crate::providers::MetadataProvider;

pub async fn unify_search(
    query: &str,
    yummy_result: ProviderResult<Vec<Anime>>,
    shiki: Option<&dyn MetadataProvider>,
) -> ProviderResult<Vec<Anime>> {
    let status = yummy_result.status;
    let yummy = yummy_result.data.unwrap_or_default();
    if !matches!(status, ProviderStatus::Ok | ProviderStatus::Partial) {
        return ProviderResult {
            status,
            data: if yummy.is_empty() { None } else { Some(yummy) },
            error: yummy_result.error,
        };
    }

    let Some(shiki) = shiki else {
        return ProviderResult::ok(yummy);
    };

    let shiki_result = shiki.search(query).await;
    let shiki_items = shiki_result.data.unwrap_or_default();
    let merged = merge_results(yummy, shiki_items);

    match shiki_result.status {
        ProviderStatus::Ok => ProviderResult::ok(merged),
        ProviderStatus::Partial => ProviderResult::partial(
            merged,
            shiki_result
                .error
                .unwrap_or_else(|| ProviderError::new("shikimori partial error", false)),
        ),
        ProviderStatus::NotFound => ProviderResult::ok(merged),
        ProviderStatus::RateLimited | ProviderStatus::Unauthorized | ProviderStatus::Error => {
            ProviderResult::partial(
                merged,
                shiki_result
                    .error
                    .unwrap_or_else(|| ProviderError::new("shikimori error", false)),
            )
        }
    }
}

fn merge_results(yummy: Vec<Anime>, shiki: Vec<Anime>) -> Vec<Anime> {
    let mut shiki_by_id = Vec::new();
    for anime in shiki {
        if let Some(key) = merge_key(&anime.id) {
            shiki_by_id.push((key, anime));
        }
    }

    let mut merged = Vec::new();
    for mut item in yummy {
        let key = merge_key(&item.id);
        if let Some(key) = key {
            if let Some((_, shiki_item)) = shiki_by_id
                .iter()
                .find(|(candidate_key, _)| *candidate_key == key)
            {
                item = merge_anime(shiki_item.clone(), item);
            }
        }
        merged.push(item);
    }

    merged
}

fn merge_anime(mut shiki: Anime, yummy: Anime) -> Anime {
    shiki.id = AnimeId {
        shikimori_id: shiki.id.shikimori_id.or(yummy.id.shikimori_id),
        mal_id: shiki.id.mal_id.or(yummy.id.mal_id),
        yummy_id: yummy.id.yummy_id.or(shiki.id.yummy_id),
    };

    let yummy_title = yummy.title;
    if shiki.title.trim().is_empty() {
        shiki.title = yummy_title.clone();
    }

    if shiki.synopsis.is_none() {
        shiki.synopsis = yummy.synopsis;
    }

    if shiki.poster_url.is_none() {
        shiki.poster_url = yummy.poster_url;
    }

    let mut alt = shiki.alt_titles;
    for title in yummy.alt_titles {
        if !alt.iter().any(|existing| existing == &title) {
            alt.push(title);
        }
    }
    if !alt.iter().any(|existing| existing == &yummy_title) {
        alt.push(yummy_title);
    }
    shiki.alt_titles = alt;

    shiki
}

fn merge_key(id: &AnimeId) -> Option<(Option<u64>, Option<u64>)> {
    if id.shikimori_id.is_none() && id.mal_id.is_none() {
        None
    } else {
        Some((id.shikimori_id, id.mal_id))
    }
}

#[cfg(test)]
mod tests;
