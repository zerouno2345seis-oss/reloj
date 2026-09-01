// Behavioural guard for the 12-unit grid resize engine.
//
// Unlike the other suites here, this one does not assert on the source text: it
// lifts the two resize functions straight out of app.js and *runs* them in a
// sandbox. Textual assertions could not have caught the bugs this file covers —
// a drag that compounded on itself, a west handle that grew eastwards, and rows
// that drifted off 12 units — because the source looked perfectly reasonable.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import test from 'node:test';

const src = await readFile(new URL('../app.js', import.meta.url), 'utf8');

/**
 * Slices one top-level function out of app.js by walking its braces.
 * Fine for these two functions, which contain no braces inside strings.
 */
function extractFunction(name) {
  const start = src.indexOf(`function ${name}(`);
  assert.ok(start > -1, `${name}() is missing from app.js`);
  let index = src.indexOf('{', start);
  let depth = 0;
  for (; index < src.length; index++) {
    if (src[index] === '{') depth++;
    else if (src[index] === '}' && --depth === 0) return src.slice(start, index + 1);
  }
  throw new Error(`${name}() is not brace-balanced`);
}

const resizeSource = `${extractFunction('resizeGridTileWidth')}\n${extractFunction('resizeGridRowHeight')}`;

/** Builds the globals the two functions read, exactly as app.js provides them. */
function makeContext(tiles, rowWeights = {}) {
  const context = {
    tileConfig: { tiles: structuredClone(tiles), rowWeights: { ...rowWeights } },
    startTilesSnapshot: structuredClone(tiles),
    startRowWeightsSnapshot: structuredClone(rowWeights),
    primarySelectedIdx: 0,
    getCanvasInsets: () => ({ top: 0, right: 0, bottom: 0, left: 0 }),
    Math,
    Number,
  };
  vm.createContext(context);
  vm.runInContext(resizeSource, context);
  return context;
}

/** Drives a resize the way onPointerMove does: dx is always measured from drag start. */
function drag(context, tileIndex, side, delta) {
  context.primarySelectedIdx = tileIndex;
  const fn = side === 'n' || side === 's' ? 'resizeGridRowHeight' : 'resizeGridTileWidth';
  vm.runInContext(`${fn}(startTilesSnapshot[${tileIndex}], '${side}', ${delta})`, context);
}

const spansOf = (context) => context.tileConfig.tiles.map((tile) => tile.colSpan);
const ONE_UNIT = 100 / 12;

const threeUpRow = [
  { id: 'a', rowIndex: 0, colSpan: 4 },
  { id: 'b', rowIndex: 0, colSpan: 4 },
  { id: 'c', rowIndex: 0, colSpan: 4 },
];

test('dragging east grows the tile and takes only from its right neighbour', () => {
  const context = makeContext(threeUpRow);
  drag(context, 1, 'e', ONE_UNIT);
  assert.deepEqual(spansOf(context), [4, 5, 3]);
});

test('dragging west grows the tile and takes only from its left neighbour', () => {
  const context = makeContext(threeUpRow);
  drag(context, 1, 'w', -ONE_UNIT);
  // The tile must grow leftwards; its right edge stays where it was.
  assert.deepEqual(spansOf(context), [3, 5, 4]);
});

test('a drag never compounds across pointermove events', () => {
  const context = makeContext(threeUpRow);
  // A real drag fires many moves, each carrying the total delta from the start.
  for (const dx of [2, 5, 9, 14, 20, 25, ONE_UNIT * 2]) drag(context, 1, 'e', dx);
  assert.deepEqual(spansOf(context), [4, 6, 2], 'final layout must reflect the last delta only');
});

test('a row always sums to exactly 12 units and never starves a tile', () => {
  for (const dx of [-500, -60, -8, 0, 8, 60, 500]) {
    const context = makeContext(threeUpRow);
    drag(context, 1, 'e', dx);
    const spans = spansOf(context);
    assert.equal(spans.reduce((sum, span) => sum + span), 12, `row broke at dx=${dx}: ${spans}`);
    assert.ok(Math.min(...spans) >= 2, `tile starved at dx=${dx}: ${spans}`);
  }
});

test('the outermost tile of a row has nothing to trade with and stays put', () => {
  const context = makeContext(threeUpRow);
  drag(context, 0, 'w', -50);
  assert.deepEqual(spansOf(context), [4, 4, 4]);
});

test('row height is measured from the drag-start weight, so it cannot run away', () => {
  const context = makeContext(threeUpRow, { 0: 1 });
  for (const dy of [2, 6, 11, 18, 25, 30]) drag(context, 0, 's', dy);
  assert.ok(
    Math.abs(context.tileConfig.rowWeights[0] - (1 + 30 * 0.045)) < 1e-9,
    `row weight compounded instead of tracking the pointer: ${context.tileConfig.rowWeights[0]}`
  );
});

test('row height stays inside sane bounds however far the pointer travels', () => {
  for (const dy of [-9999, -100, 0, 100, 9999]) {
    const context = makeContext(threeUpRow, { 0: 1 });
    drag(context, 0, 's', dy);
    const weight = context.tileConfig.rowWeights[0];
    assert.ok(weight >= 0.22 && weight <= 4, `weight out of bounds at dy=${dy}: ${weight}`);
  }
});

test('dragging north shrinks the row that dragging south grows', () => {
  const north = makeContext(threeUpRow, { 0: 1 });
  const south = makeContext(threeUpRow, { 0: 1 });
  drag(north, 0, 'n', 20);
  drag(south, 0, 's', 20);
  assert.ok(north.tileConfig.rowWeights[0] < 1, 'north drag should shrink the row');
  assert.ok(south.tileConfig.rowWeights[0] > 1, 'south drag should grow the row');
});
