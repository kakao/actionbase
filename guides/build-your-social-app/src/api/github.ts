import {apiFetch} from './client';

export function getStarsAsTag(
  owner: string,
  repo: string
) {
  return apiFetch<string>(
    `https://img.shields.io/github/stars/${owner}/${repo}?style=social`,
    undefined,
    false
  );
}

