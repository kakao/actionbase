/**
 * Re-fetch the mutation state diagram, which used to be loaded from plantuml.com at view time.
 *
 * The docs serve a committed snapshot so the page keeps working if plantuml.com changes.
 * The source is the "Edit on PlantUML" link in design/mutation.mdx: edit the diagram there,
 * update the link, then re-run this.
 *
 * To go back to the live render instead, point the Markdown image at the source URL below.
 *
 * Usage (from website/):
 * node scripts/snapshot-remote-images.mjs
 */

import { readFile, writeFile } from 'fs/promises';

const mutationDoc = await readFile('src/content/docs/design/mutation.mdx', 'utf8');
const [, plantumlEncoded] = mutationDoc.match(/plantuml\.com\/plantuml\/uml\/([\w-]+)/) ?? [];
if (!plantumlEncoded) throw new Error('No "Edit on PlantUML" link found in design/mutation.mdx');

const source = `https://www.plantuml.com/plantuml/svg/${plantumlEncoded}`;
const target = 'public/images/design/mutation-state-transitions.svg';

const res = await fetch(source);
if (!res.ok) throw new Error(`${source} responded ${res.status}`);
await writeFile(target, Buffer.from(await res.arrayBuffer()));
console.log(`${target} <- ${source}`);
