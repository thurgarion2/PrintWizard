import {
    RootEventTree,
    POSITION,
    scopedStateFromEventTree,
    traceViewFromScopedState,
    TraceIterator,
    TRACEINDEX
} from './eventTree'

import {
    ASSIGN,
    EVENT,
    EventKinds,
    Value,
    Identifier,
    Object,
    NODEID,
    NodeFormat
} from './event';
export {
    traceElement,
    vbox,
    objectElement,
    hbox
};

/**************
 ********* CONSTANTS
 **************/

const Result_Style = 'result';
const Update_Style = 'updates';
const Click_Style = 'clickable';
const Ast_Node_Syle = 'astNode';
const HBox_Sytle = '';
const VBox_Sytle = '';
const Item_Sytle = '';
const EmojiStart = 0x1F0C;
const EmojiEnd = 0x1F9FF;
const SPACE = '\u00A0';


/**************
 ********* Types
 **************/

interface UIElement {
    display: (painter: Painter) => void
}

interface Painter {
    paint: (node: Node) => void
}

interface DisplayObject {
    addObject: (obj: Object) => void
}

/**************
 ********* UI elements
 **************/

/********
 **** Vbox
 ********/

interface VBox extends UIElement {
    update: () => void
}

function vbox(items: () => UIElement[]): VBox {
    const box = divEl([], VBox_Sytle);
    const painter: Painter = {
        paint(node) {
            box.appendChild(node)
        }
    }
    items().map(item => item.display(painter));

    return {
        display(painter) {
            painter.paint(box)
        },
        update() {
            box.innerHTML = '';
            items()
                .map(item => item.display(painter))
        }
    }

}

/********
 **** Hbox
 ********/

interface HBox extends UIElement {

}

function hbox(items: () => UIElement[]): HBox {
    const cols = columns([])
    const table = tableEl([cols], HBox_Sytle);

    const painter: Painter = {
        paint(node) {
            cols.appendChild(cellElement(node))
        }
    }


    items().map(item => item.display(painter));

    return {
        display(painter) {
            painter.paint(table)
        }
    }

}

/********
 **** Item
 ********/


interface Item extends UIElement {

}

function makeItem(content: Node[]): Item {
    const node = divEl(content, Item_Sytle);
    return {
        display(painter) {
            painter.paint(node);
        }
    }
}

/**************
 ********* Component UI
 **************/


/********
 **** Trace UI
 ********/

function traceElement(tree: RootEventTree, model: DisplayObject): UIElement {
    const scopedStateTree = scopedStateFromEventTree<ASSIGNSTATE, IDENTCONTEXT>(
        tree,
        zero(),
        updateState,
        updateContext
    );

    const traceView = traceViewFromScopedState(scopedStateTree);
    const windowSize = 40;
    let startWindow = traceView.start;
    const [idx, [assign, ident]] = startWindow.element
    if (!isDisplayed(idx, assign, ident)) {
        const next = nextDisplayEvent(startWindow)
        startWindow = next !== undefined ? next : startWindow;
    }

    const items = function () {
        let items = [];
        let it: TraceIterator<[ASSIGNSTATE, IDENTCONTEXT]> | undefined = startWindow;
        for (let count = 0; count < windowSize && it != undefined; ++count) {
            items.push(it.element)
            it = nextDisplayEvent(it)
        }

        return items.map(item => {
            const [index, [state, ctx]] = item;
            const event = index.event;
            const nodes = astNodeElement(
                ctx.ident,
                event.nodeId,
                state.assigns,
                undefined,
                model
            );
            return makeItem(nodes);
        });
    }

    const box = vbox(items);

    document.addEventListener("keypress", (event: KeyboardEvent) => {
        const keyName = event.key;
        switch (keyName) {
            case 's':
                const next = nextDisplayEvent(startWindow);
                startWindow = next === undefined ? startWindow : next;
                box.update()
                break;
            case 'w':
                const prev = prevDisplayEvent(startWindow);
                startWindow = prev === undefined ? startWindow : prev;
                box.update()
                break;
        }

    });

    return box;
}

/********
 **** helpers to dispaly the trace
 ********/

function prevDisplayEvent(iter: TraceIterator<[ASSIGNSTATE, IDENTCONTEXT]>): TraceIterator<[ASSIGNSTATE, IDENTCONTEXT]> | undefined {
    let it: undefined | TraceIterator<[ASSIGNSTATE, IDENTCONTEXT]> = iter.predecessor();
    while (it !== undefined) {
        const [idx, [assign, ident]] = it.element
        if (isDisplayed(idx, assign, ident)) {
            return it;
        }
        it = it.predecessor();
    }
    return it;
}

function nextDisplayEvent(iter: TraceIterator<[ASSIGNSTATE, IDENTCONTEXT]>): TraceIterator<[ASSIGNSTATE, IDENTCONTEXT]> | undefined {

    let it: undefined | TraceIterator<[ASSIGNSTATE, IDENTCONTEXT]> = iter.successor();
    while (it !== undefined) {
        const [idx, [assign, ident]] = it.element
        if (isDisplayed(idx, assign, ident)) {
            return it;
        }
        it = it.successor();
    }
    return it;
}

function isDisplayed(idx: TRACEINDEX, assign: ASSIGNSTATE, ident: IDENTCONTEXT): boolean {
    const event = idx.event
    switch (idx.pos) {
        case POSITION.START:
            return false;
        case POSITION.END:
            return event.kind != EventKinds.Flow;
        default:
            return false;
    }
}


/********
 **** utils
 ********/

type IDENTCONTEXT = {
    ident: number
}

type ASSIGNSTATE = {
    assigns: ASSIGN[]
}

function zero(): IDENTCONTEXT {
    return { ident: 0 };
}

function updateContext(event: EVENT, ctx: IDENTCONTEXT): IDENTCONTEXT {
    switch (event.kind) {
        case EventKinds.Flow:
            return { ident: ctx.ident + 1 };
        default:
            return ctx;
    }
}

function updateState(event: EVENT, states: ASSIGNSTATE[]): ASSIGNSTATE {
    switch (event.kind) {
        case EventKinds.Update:
            return { assigns: [{ varName: event.varName, value: event.value }] }
        default:
            return {
                assigns: states
                    .map(s => s.assigns)
                    .reduce((acc: ASSIGN[], val: ASSIGN[]) => acc.concat(val), [])
            }
    }
}

/********
 **** Object UI
 ********/

function objectElement(obj: Object): UIElement {
    let nodes: Node[] = [...line([
        textEl(emojiUnicode(obj.id)),
        textEl(':'),
        textEl(shortClassName(obj.class))])]

    const model: any = undefined

    obj.fields
        .map(field => {
            const identifier = displayIdentifier(field.identifier, model)
            const value = displayValue(field.value, model)
            nodes.push(...line([
                textEl(identationRepr(1)),
                ...identifier,
                textEl(" = "),
                value
            ]))
        })
    return makeItem(nodes);
}

/********
 **** utils
 ********/

function shortClassName(name: string): string {
    const parts = name.split(".");
    return parts[parts.length - 1];

}

/**************
 ********* UI for Event
 **************/

function astNodeElement(
    identation: number,
    node: NODEID,
    assings: ASSIGN[] | undefined,
    result: Value | undefined,
    model: DisplayObject): Node[] {

    let event: Node[]

    switch (node.type) {
        case 'UnknownFormat':
            return [textEl('unknown ' + node.line)]

        case 'NodeFormat':
            return elementForKnownNodeFormat(
                identation,
                node,
                assings,
                result,
                model)

    }
}

function elementForKnownNodeFormat(
    identation: number,
    node: NodeFormat,
    assings: ASSIGN[] | undefined,
    result: Value | undefined,
    model: DisplayObject): Node[] {

    let res: Node[] = []
    if (result !== undefined) {
        res.push(spanEl([textEl(" -> "), displayValue(result, model)], Result_Style));
    }

    let assigns_: Node[] = []
    if (assings !== undefined) {
        assigns_.push(...[
            textEl(" "),
            spanEl(displayAssigns(assings, model), Update_Style)
        ])
    }

    return [
        formatLineNumber(node.lineNumber),
        textEl(identationRepr(identation)),
        ... formatLine(node, assigns_, res)
    ]
}

/********
 **** utils
 ********/

function formatLineNumber(lineNumber: number): Node {
    const constSize = 4;
    const repr = lineNumber.toString();
    console.assert(repr.length <= 4);
    return textEl(SPACE.repeat(constSize - repr.length) + repr);
}

function formatLine(node: NodeFormat, assings: Node[], result: Node[]): Node[] {
    const line = node.line;
    const before = line.slice(0,node.startCol);
    const astFocused = line.slice(node.startCol,node.endCol)
    const after = line.slice(node.endCol)
    return [
        textEl(before),
        spanEl([textEl(astFocused)], Ast_Node_Syle),
        ... result,
        ... assings,
        textEl(after)
    ];
}

function displayAssigns(assigns: ASSIGN[], model: DisplayObject): Node[] {
    return assigns
        .map(assign => displayAssign(assign, model))
        .reduce((acc, assign) => {
            if (acc.length > 0) {
                acc.push(textEl(", "));
            }
            acc.push(...assign);
            return acc;
        }, [])
}

function displayAssign(assigns: ASSIGN, model: DisplayObject): Node[] {
    return [...displayIdentifier(assigns.varName, model),
    textEl('='),
    displayValue(assigns.value, model)]
}

/**************
 ********* UI for Identifier
 **************/


function displayIdentifier(ident: Identifier, model: DisplayObject): Node[] {
    switch (ident.type) {
        case 'LocalIdentifier':
            return [textEl(ident.name)];
        case 'Field':
            return [displayValue(ident.obj, model), textEl("." + ident.name)];
        case 'FiledInObject':
            return [textEl(ident.name)];
    }

}

/**************
 ********* UI for Value
 **************/

function displayValue(value: Value, model: DisplayObject): Node {
    switch (value.type) {
        case 'Literal':
            return textEl(value.value.toString());
        case 'Object':
            return clickAction(
                [textEl(emojiUnicode(value.id))],
                () => { model.addObject(value) }
            );
        case 'ObjectWithoutFields':
            return textEl(emojiUnicode(value.id));
    }
}




/********
 **** formatting utils for view elements
 ********/

function emojiUnicode(n: number): string {
    const nbEmojis = EmojiEnd - EmojiStart + 1;
    const codePoint = (n % nbEmojis) + EmojiStart;
    return String.fromCodePoint(codePoint);
}


let storeIdent: Map<number, string> = new Map<number, string>([
    [0, ""]
]);

function identationRepr(ident: number): string {
    console.assert(ident >= 0)

    if (!storeIdent.has(ident)) {
        storeIdent.set(ident, SPACE.repeat(6) + identationRepr(ident - 1));
    }
    const res = storeIdent.get(ident);
    return res === undefined ? '' : res;
}

/**************
 ********* UI utils
 **************/

function line(elements: Node[]): Node[] {
    return [...elements, brEl()]
}

function clickAction(elements: Node[], action: () => void): HTMLElement {
    let span = spanEl(elements, Click_Style);
    span.addEventListener('click', action)
    return span;
}

function spanEl(elements: Node[], css: string | undefined): HTMLElement {
    let span = document.createElement("span");
    elements.map(child => span.appendChild(child));

    if (css !== undefined) {
        span.className = css;
    }
    return span;
}

function divEl(elements: Node[], css: string | undefined): HTMLElement {
    let span = document.createElement("div");
    elements.map(child => span.appendChild(child));

    if (css !== undefined) {
        span.className = css;
    }
    return span;
}

function tableEl(rows: HTMLTableRowElement[], css: string | undefined): HTMLElement {
    let table = document.createElement("table");
    rows.map(row => table.appendChild(row));

    setCssClass(table, css);
    return table;
}

function columns(cols: Node[]): HTMLTableRowElement {
    const row = document.createElement("tr");
    cols
        .map(col => {
            const cell = cellElement(col);
            row.appendChild(cell);
        })
    return row;
}

function cellElement(node: Node): HTMLTableCellElement {
    const cell = document.createElement("td");
    cell.appendChild(node);
    return cell;
}


function brEl(): HTMLElement {
    return document.createElement("br");
}

function setCssClass(node: HTMLElement, css: string | undefined) {
    if (css !== undefined) {
        node.className = css;
    }
}


function textEl(text: string): Text {
    return document.createTextNode(text);
}
