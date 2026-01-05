import {apiFetch} from "../wrapper/apiClient";

export const getStarsAsTag = (
  owner: string,
  repo: string
) => apiFetch<string>(
  `https://img.shields.io/github/stars/${owner}/${repo}?style=social`,
  undefined,
  false
)

