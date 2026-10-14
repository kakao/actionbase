// Astro doesn't prefix `base` onto root-relative URLs written in Markdown/MDX,
// so `/quick-start/` would escape the site when it's served under a sub-path.
// This runs before starlight-links-validator, which then sees the final URLs.

const URL_ATTRIBUTES = new Set(['href', 'src']);

export function withBase(url, base) {
  if (base === '/' || typeof url !== 'string') return url;
  if (!url.startsWith('/') || url.startsWith('//') || url.startsWith(base)) return url;
  return base + url.slice(1);
}

function rewrite(node, base) {
  if (node.type === 'link' || node.type === 'image' || node.type === 'definition') {
    node.url = withBase(node.url, base);
  } else if (node.type === 'mdxJsxFlowElement' || node.type === 'mdxJsxTextElement') {
    for (const attribute of node.attributes ?? []) {
      if (attribute.type === 'mdxJsxAttribute' && URL_ATTRIBUTES.has(attribute.name)) {
        attribute.value = withBase(attribute.value, base);
      }
    }
  }
  for (const child of node.children ?? []) rewrite(child, base);
}

export default function remarkBasePath({ base }) {
  return (tree) => rewrite(tree, base);
}
