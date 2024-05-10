import {
    EVENT,
    EventKind,
    Value,
    Identifier,
    ObjectValue,
    NODEID,
    NodeFormat,
    Label,
    UPDATE,
    LabelPos,
    EventSubKind,
    Result,
    Data,
    Write,
    writeIsDefined,
    resultIsDefined
} from './event';
import { ScopedLabel } from './treeTransforms';
export {
    isDisplayed,
    labelItem,
    initialContext,
    propagateItemContext,
    aggregateItemState
};

/**************
 ********* CONSTANTS
 **************/

const Result_Style = 'result';
const Update_Style = 'updates';
const Click_Style = 'clickable';
const Ast_Node_Syle = 'astNode';
const EmojiStart = 0x1F0C;
const EmojiEnd = 0x1F9FF;
const SPACE = '\u00A0';


/*******************************************************
 **************** transform ******************
 *******************************************************/

/**************
 ********* Types
 **************/

interface LabelItem {
    html: () => HTMLElement
}

type ItemContext = {
    ident: number
}

type ItemState = {
    writes: Write[]
}

interface DisplayObject {
    addObject: (obj: ObjectValue) => void
}

/**************
 ********* transforms
 **************/

function labelItem(label: ScopedLabel<ItemContext, ItemState>): LabelItem {
    const ctx = label.context
    const event = label.label.event
    const state = label.state

    if (event === undefined)
        debugger;

    return {
        html: () => itemHtml(
            ctx,
            event.description.nodeId,
            state.store.writes,
            event.data,
            { addObject: (obj: ObjectValue) => { } }
        )
    }
}

function isDisplayed(label: ScopedLabel<ItemContext, ItemState>): boolean {
    const ctx = label.context
    const l = label.label
    const event = label.label.event
    const state = label.state

    switch (event.description.type.kind) {
        case EventKind.Flow: return false;
        case EventKind.Update: return false;
        case EventKind.Expression:
        case EventKind.Statement:
            switch (event.description.type.subkind) {
                case EventSubKind.Simple:
                    return l.pos === LabelPos.END;
                case EventSubKind.VoidCall:
                case EventSubKind.ResultCall:
                    return l.pos === LabelPos.CALL;
                default:
                    return false;
            }
    }
}


/**************
 ********* Context/State computation
 **************/

const initialContext: ItemContext = {
    ident: 0
}



function propagateItemContext(event: EVENT, ctx: ItemContext): ItemContext {
    switch (event.description.type.kind) {
        case EventKind.Flow:
            return { ident: ctx.ident + 1 };
        default:
            return ctx;
    }
}

function aggregateItemState(event: EVENT, states: ItemState[]): ItemState {
    switch (event.description.type.kind) {
        case EventKind.Update:
            const update: UPDATE = event as UPDATE
            return { writes: writeIsDefined(update.data) ? [update.data] : [] };

        default:
            return {
                writes: states
                    .map(s => s.writes)
                    .reduce((acc: Write[], val: Write[]) => acc.concat(val), [])
            }
    }
}

/********
 **** Object UI
 ********/

// function objectElement(obj: ObjectValue): UIElement {
//     let nodes: Node[] = [...line([
//         textEl(emojiUnicode(obj.id)),
//         textEl(':'),
//         textEl(shortClassName(obj.class))])]

//     const model: any = undefined

//     obj.fields
//         .map(field => {
//             const identifier = displayIdentifier(field.identifier, model)
//             const value = displayValue(field.value, model)
//             nodes.push(...line([
//                 textEl(identationRepr(1)),
//                 ...identifier,
//                 textEl(" = "),
//                 value
//             ]))
//         })
//     return makeItem(nodes);
// }

/********
 **** utils
 ********/

function shortClassName(name: string): string {
    const parts = name.split(".");
    return parts[parts.length - 1];

}

/*******************************************************
 **************** Html generation ******************
 *******************************************************/




function itemHtml(
    ctx: ItemContext,
    node: NODEID,
    assings: Write[],
    data: Data,
    model: DisplayObject): HTMLElement {



    switch (node.type) {
        case 'UnknownFormat':
            return box(PrintWizardStyle.TraceItem, [textEl('unknown ' + node.line), brEl()])

        case 'NodeFormat':
            const nodes = [
                textBox(PrintWizardStyle.LineNumber, node.lineNumber.toString()),
                textBox(PrintWizardStyle.Identation, identationRepr(ctx.ident)),
                formatCode(node, assings.map(a => dataElement(a, model)), [dataElement(data, model)])
            ]
            return box(PrintWizardStyle.TraceItem, nodes)

    }
}

/********
 **** utils
 ********/


function formatCode(node: NodeFormat, assings: Node[], result: Node[]): HTMLElement {
    const line = node.line;
    const before = line.slice(0, node.startCol);
    const evalutedExpression = line.slice(node.startCol, node.endCol)
    const after = line.slice(node.endCol)
    return box(PrintWizardStyle.Code, [
        textEl(before),
        textBox(PrintWizardStyle.Expression, evalutedExpression),
        ...result,
        ...assings,
        textEl(after)
    ]);
}


function dataElement(data: Data, model: DisplayObject): HTMLElement {
    switch (data.type) {
        case 'empty':
            return empty();
        case 'result':
            if (resultIsDefined(data)) {
                return box(PrintWizardStyle.Result, [textEl("\u27A1"), displayValue(data.value as Value, model)]);
            } else {
                return empty();
            }
        case 'write':
            if (writeIsDefined(data)) {
                return box(PrintWizardStyle.Write,
                    [...displayIdentifier(data.varName as Identifier, model),
                    textEl('='),
                    displayValue(data.value as Value, model)
                    ]
                )
            } else {
                return empty();
            }
    }
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

/*******************************************************
 **************** UI primitive  ******************
 *******************************************************/

/**************
 ********* styles
 **************/

enum PrintWizardStyle {
    LineNumber = 'line_number',
    TraceItem = 'trace_item',
    Identation = 'indentation',
    Code = 'code',
    Expression = 'expression',
    Result = 'result',
    Write = 'write'
}

/**************
 ********* primitives
 **************/


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

function empty(): HTMLElement {
    return spanEl([], undefined);
}

function textBox(style: PrintWizardStyle, txt: string) {
    let b = box(style, [])
    b.appendChild(textEl(txt))
    return b;
}

function box(style: PrintWizardStyle, elements: Node[]) {
    let div = document.createElement("div");
    div.className = style;
    elements.map(child => div.appendChild(child));
    return div;
}


function brEl(): HTMLElement {
    return document.createElement("br");
}


function textEl(text: string): Text {
    return document.createTextNode(text);
}
