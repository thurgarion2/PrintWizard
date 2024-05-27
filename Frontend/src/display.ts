import {
    EVENT,
    InstanceReference,
    Value,
    Identifier,
    NodeSourceFormat,
    UPDATE,
    LabelPos,
    Result,
    EventKind,
    EventSubKind,
    DataType,
    Data,
    Write,
    Token,
    FieldIdentifier,
    PresentInSourceCode,
    valueFromJson,
    ArgsValues
} from './event';
import { objectDataCache } from './fetch';
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
    inspect: (obj: InstanceReference) => void
}

/**************
 ********* transforms
 **************/

function labelItem(label: ScopedLabel<ItemContext, ItemState>): LabelItem {
    const ctx = label.context
    const event = label.label.event
    const state = label.state

    return {
        html: () => itemHtml(
            ctx,
            event.instanceInfo.nodeId,
            state.store.writes,
            event
        )
    }
}

function isDisplayed(label: ScopedLabel<ItemContext, ItemState>): boolean {
    const ctx = label.context
    const l = label.label
    const event = label.label.event
    const state = label.state

    switch (event.description.kind) {
        case EventKind.Flow: return false;
        case EventKind.Update: return false;
        case EventKind.Expression:
        case EventKind.Statement:
            switch (event.description.subkind) {
                case EventSubKind.Simple:
                    return l.pos === LabelPos.END;
                case EventSubKind.CallVoid:
                case EventSubKind.ResultCall:
                    return l.pos === LabelPos.CALL;
                default:
                    return false;
            }
        default:
            throw new Error("missing case " + event.description.kind);
    }
}


/**************
 ********* Context/State computation
 **************/

const initialContext: ItemContext = {
    ident: 0
}



function propagateItemContext(event: EVENT, ctx: ItemContext): ItemContext {
    switch (event.description.kind) {
        case EventKind.Flow:
            return { ident: ctx.ident + 1 };
        default:
            return ctx;
    }
}

function aggregateItemState(event: EVENT, states: ItemState[]): ItemState {
    switch (event.description.kind) {
        case EventKind.Update:
            const update: UPDATE = event as UPDATE
            return { writes: update.write ? [update.write] : [] };

        default:
            return {
                writes: states
                    .map(s => s.writes)
                    .reduce((acc: Write[], val: Write[]) => acc.concat(val), [])
            }
    }
}


/*******************************************************
 **************** HTML ******************
 *******************************************************/

/**************
 ********* NodeSourceFormat
 **************/

/********
**** transformation
********/

const SPACES_PER_IDENT_LEVEL = 6


function nodeSythaxToHtml(
    synthax: PresentInSourceCode,
    result: Result | undefined,
    assigns: Write[],
    indentLevel: number,
    inspector: DisplayObject,
    childrenValues: ArgsValues | undefined): HTMLElement {



    const resultHtml = result === undefined ? [] : [dataElement(result, inspector)]
    const assignsHtml = assigns.map(assign => assignToHtml(assign, inspector))

    console.assert(childrenValues === undefined || childrenValues.values.length === synthax.children.length)

    let code: Node[] = []

    code.push(...tokenToHtml(synthax.prefix, (index: number) => undefined))


    code.push(box(
        PrintWizardStyle.Expression,
        synthax.expression.tokens.flatMap(token => tokenToHtml(token, argumentValue(childrenValues, inspector))
        )))
    code.push(...[...resultHtml, ...assignsHtml])
    code.push(...tokenToHtml(synthax.suffix, (index: number) => undefined))


    return box(PrintWizardStyle.Code, [
        box(PrintWizardStyle.LineNumbers,
            lineRange(synthax.startLine, synthax.endLine + 1)
                .map(l => textBox(PrintWizardStyle.LineNumber, l.toString()))),
        widthBox(SPACES_PER_IDENT_LEVEL * indentLevel),
        defaultBox(code)
    ])
}

/********
**** api
********/



/********
**** helper
********/

function argumentValue(childrenValues: ArgsValues | undefined, inspector: DisplayObject): (index: number) => Node[] | undefined {
    if(childrenValues === undefined){
        return (index : number) => undefined
    }else{
        return (index : number) => {
            const value = childrenValues.values[index]
            return value === undefined ? undefined : [textEl(':'), displayValue(value, inspector)];
        }
    }

}

//inclusive, inc
function lineRange(startLine: number, endLine: number): number[] {
    let range = []
    for (let i = startLine; i < endLine; ++i) {
        range.push(i)
    }
    return range;
}


function tokenToHtml(token: Token, childToNode: (index: number) => Node[] | undefined): Node[] {
    switch (token.kind) {
        case 'LineStart':
            return [brEl()]
        case 'Text':
            return [textEl(token.text)]
        case 'Child':
            const childNode = childToNode(token.childIndex)
          
            return [textEl(token.text), ... (childNode === undefined ? [empty()] : childNode)]
    }
}


function resultToHtml(result: Result, inspector: DisplayObject): HTMLElement {
    return box(
        PrintWizardStyle.Result,
        [textEl("\u27A1"), displayValue(result.value as Value, inspector)]
    );
}

function assignToHtml(assign: Write, inspector: DisplayObject): HTMLElement {
    return box(PrintWizardStyle.Write,
        [...displayIdentifier(assign.identifier, inspector),
        textEl('='),
        displayValue(assign.value as Value, inspector)
        ]
    )
}

/**************
 ********* Object
 **************/

type ObjectData = {
    self: InstanceReference,
    fields: Field[]
}

function objectHtml(obj: ObjectData): HTMLElement {
    return box(PrintWizardStyle.TraceItem, [
        box(PrintWizardStyle.Line, [referenceHtml(obj.self)]),
        ...obj.fields.map(fieldHtml)
    ])
}

type Field = {
    identifier: FieldIdentifier,
    value: Value
}

function fieldHtml(field: Field): HTMLElement {

    return box(PrintWizardStyle.Line, [
        textEl(field.identifier.name),
        textEl(" = "),
        displayValue(valueFromJson(field.value), { inspect: (obj) => { } })
    ])
}

class ObjectInspector implements DisplayObject {
    html = box(PrintWizardStyle.Inspector, [])
    displayedRefs: Set<string> = new Set()

    constructor() {
    }

    keyOf(ref: InstanceReference): string {
        return ref.pointer.toString() + '-' + ref.version.toString();
    }

    inspect(ref: InstanceReference) {
        const k = this.keyOf(ref)
        if (!this.displayedRefs.has(k)) {
            this.displayedRefs.add(k)
            this.html.appendChild(this.objectToHtml(ref))
        }
    }

    objectToHtml(ref: InstanceReference): HTMLElement {
        const key = this.keyOf(ref)

        const data = objectDataCache().data()

        switch (data.type) {
            case 'failure':
                return box(PrintWizardStyle.TraceItem, [
                    box(PrintWizardStyle.TraceItem, [textEl(ref.className.className)]),
                    box(PrintWizardStyle.TraceItem, [textEl('data not loaded')])
                ])
            case 'success':
                const objData: undefined | ObjectData = data.payload[key]
                if (objData === undefined) {
                    return box(PrintWizardStyle.TraceItem, [
                        box(PrintWizardStyle.Line, [referenceHtml(ref)]),
                        box(PrintWizardStyle.Line, [textEl('object not found')])
                    ])
                } else {

                    return objectHtml(objData)
                }
        }
    }
}


/**************
 ********* item
 **************/




function itemHtml(
    ctx: ItemContext,
    node: NodeSourceFormat,
    assings: Write[],
    event: EVENT): HTMLElement {


    switch (node.kind) {
        case 'absent':
            return empty()

        case 'presentInSourceCode':
            const inspector = new ObjectInspector()

            return box(PrintWizardStyle.TraceItem, [
                inspector.html,
                nodeSythaxToHtml(
                    node,
                    (event as any).result,
                    assings,
                    ctx.ident,
                    inspector,
                    (event as any).argsValues
                )
            ])

    }
}

/********
 **** utils
 ********/


function dataElement(data: Data, model: DisplayObject): HTMLElement {
    switch (data.dataType) {
        case DataType.Result:
            return box(PrintWizardStyle.Result, [textEl("\u27A1"), displayValue(data.value as Value, model)]);
        case DataType.Write:
            return box(PrintWizardStyle.Write,
                [...displayIdentifier(data.identifier, model),
                textEl('='),
                displayValue(data.value as Value, model)
                ]
            )
        case DataType.InstanceRef:
        case DataType.StaticReference:
            throw new Error("not implemented");
        case DataType.ArgsValues:
        case DataType.FieldIdentifier:
        case DataType.LocalIdentifier:
            return empty()
    }
}



/**************
 ********* UI for Identifier
 **************/


function displayIdentifier(ident: Identifier, model: DisplayObject): Node[] {
    switch (ident.dataType) {
        case DataType.LocalIdentifier:

            return [textEl(ident.name)];
        case DataType.FieldIdentifier:
            const owner = ident.owner
            switch (owner.dataType) {
                case DataType.StaticReference:
                    return [textEl(owner.className.className + '.' + ident.name)];
                case DataType.InstanceRef:
                    return [textEl(emojiUnicode(owner.pointer) + '.' + ident.name)];
            }
    }
}

/**************
 ********* UI for reference
 **************/

function referenceHtml(reference: InstanceReference): Node {
    if (Number.isNaN(reference.pointer))
        debugger;
    return textEl(reference.className.className + '$' + emojiUnicode(reference.pointer));
}


/**************
 ********* UI for Value
 **************/

function displayValue(value: Value, actions: DisplayObject): Node {
    switch (value.dataType) {
        case DataType.Literal:
            const repr = value.kind === "null" ? "null" : value.value.toString()
            return textEl(repr);
        case DataType.InstanceRef:
            return clickAction(
                [referenceHtml(value)],
                () => { actions.inspect(value) }
            );
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



/*******************************************************
 **************** UI primitive  ******************
 *******************************************************/

/**************
 ********* styles
 **************/

enum PrintWizardStyle {
    LineNumbers = 'line_numbers',
    LineNumber = 'line_number',
    TraceItem = 'trace_item',
    Identation = 'indentation',
    Code = 'code',
    Expression = 'expression',
    Result = 'result',
    Write = 'write',
    Inspector = 'inspector',
    Button = 'button',
    Line = 'line'
}

/**************
 ********* primitives
 **************/


function clickAction(elements: Node[], action: () => void): HTMLElement {
    let span = spanEl(elements, PrintWizardStyle.Button);
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

function defaultBox(elements: Node[]) {
    let div = document.createElement("div");
    elements.map(child => div.appendChild(child));
    return div;
}

function widthBox(width: number) {
    let div = document.createElement("div");
    div.style.visibility = 'hidden';
    div.innerText = '-'.repeat(width)
    return div;
}



function brEl(): HTMLElement {
    return document.createElement("br");
}


function textEl(text: string): Text {
    return document.createTextNode(text);
}
