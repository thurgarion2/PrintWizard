import {
    Event,
    EventKind,
    EventKindTypes,
    ExecutionStepTypes,
    SimpleExpression,
    Call,
    VoidCall,
    InstanceReference,
    Value,
    Identifier,
    NodeSourceFormat,
    Result,
    DataType,
    Data,
    Write,
    Token,
    FieldIdentifier,
    PresentInSourceCode,
    valueFromJson,
    ArgsValues,
    ExecutionStep
} from './event';
import { objectDataCache } from './fetch';
import { EventWithContext } from './treeTransforms';
export {
    initialContext,
    propagateItemContext,
    aggregateItemState,
    traceItem
};

/**************
 ********* CONSTANTS
 **************/

const EmojiStart = 0x1F600;
const EmojiEnd = 0x1F64F;
const SPACE = '\u00A0';


/*******************************************************
 **************** display tree ******************
 *******************************************************/

/**************
 ********* Types
 **************/

interface TraceItem {
    html: () => HTMLElement,

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

function traceItem(event: EventWithContext<ItemContext, ItemState>): TraceItem {

    type HelperResult = {
        item: HTMLElement
        defaultHidden: HTMLElement[]
    }

    function helper(event: EventWithContext<ItemContext, ItemState>): HelperResult {
        let trace: HTMLElement[] = []
        let defaultHidden: HTMLElement[] = []
        let mainExecutionStep: undefined | HTMLElement = undefined
        const codeToHtml = event.kind.type === EventKindTypes.Statement ? statmentCodeToHtml : subStatmentCodeToHtml;

        event.executions().forEach(child => {
            switch (child.type) {
                case 'ExecutionStep':
                    console.assert(mainExecutionStep === undefined, 'more than 1 execution in an event')
                    mainExecutionStep = itemToHtml(event.context, event.state.writes, child, codeToHtml)
                    trace.push(mainExecutionStep)
                    break;
                case 'Event':
                    const res = helper(child)
                    trace.push(res.item)

                    defaultHidden.push(...res.defaultHidden)

            }
        })

        switch (event.kind.type) {
            case EventKindTypes.Flow:
                console.assert(defaultHidden.length === 0, 'should not have any hidden step')
                return {
                    item: defaultBox(trace),
                    defaultHidden: []
                }
            case EventKindTypes.Update:
                return {
                    item: empty(),
                    defaultHidden: []
                }
            case EventKindTypes.Statement:
                console.assert(mainExecutionStep !== undefined)
                offToggleBox(mainExecutionStep,
                    () => { defaultHidden.forEach(hide) },
                    () => { defaultHidden.forEach(show) })
                return {
                    item: defaultBox(trace),
                    defaultHidden: []
                }
            case EventKindTypes.SubStatement:
                console.assert(mainExecutionStep !== undefined)
                defaultHidden.push(mainExecutionStep)
                return {
                    item: defaultBox(trace),
                    defaultHidden: defaultHidden
                }
        }
    }
    let html = helper(event).item

    return {
        html: () => html
    }
}


/**************
 ********* Context/State computation
 **************/

const initialContext: ItemContext = {
    ident: 0
}



function propagateItemContext(event: Event, ctx: ItemContext): ItemContext {
    switch (event.kind.type) {
        case EventKindTypes.Flow:
            return { ident: ctx.ident + 1 };
        default:
            return ctx;
    }
}

function aggregateItemState(event: Event, states: ItemState[]): ItemState {
    switch (event.kind.type) {
        case EventKindTypes.Update:
            return { writes: event.kind.write ? [event.kind.write] : [] };

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
**** types 
********/

type NodeData = {
    result: Value | undefined,
    assigns: Write[],
    childrenValues: Value[]
}

/********
**** transformation
********/

function nodeData(step: ExecutionStep, assigns: Write[]) {
    return {
        result: step.kind.result,
        assigns: assigns,
        childrenValues: step.kind.argVaules === undefined ? [] : step.kind.argVaules
    }
}

const SPACES_PER_IDENT_LEVEL = 6


function nodeSythaxToHtml(
    synthax: PresentInSourceCode,
    indentLevel: number,
    inspector: DisplayObject,
    nodeData: NodeData,
    formatCode: (synthax: PresentInSourceCode,
        inspector: DisplayObject,
        nodeData: NodeData) => HTMLElement): HTMLElement {


    return box(PrintWizardStyle.Code, [
        box(PrintWizardStyle.LineNumbers,
            lineRange(synthax.startLine, synthax.endLine + 1)
                .map(l => textBox(PrintWizardStyle.LineNumber, l.toString()))),
        widthBox(SPACES_PER_IDENT_LEVEL * indentLevel),
        formatCode(synthax, inspector, nodeData)
    ])
}

function statmentCodeToHtml(
    synthax: PresentInSourceCode,
    inspector: DisplayObject,
    nodeData: NodeData): HTMLElement {

    console.assert(
        nodeData.childrenValues === undefined ||
        nodeData.childrenValues.length === synthax.children.length)

    const resultHtml = nodeData.result === undefined ?
        [] :
        [box(PrintWizardStyle.Result, [textEl("\u27A1"), displayValue(nodeData.result, inspector)])];


    let code: Node[] = [
        textEl(synthax.prefix.text),
        box(
            PrintWizardStyle.Expression,
            synthax.expression.tokens.flatMap(token => tokenToHtml(token, argumentValue(nodeData.childrenValues, inspector))
            )),
        ...resultHtml,
        ...nodeData.assigns.map(assign => assignToHtml(assign, inspector)),
        textEl(synthax.suffix.text)
    ]
    return defaultBox(code);
}

function subStatmentCodeToHtml(
    synthax: PresentInSourceCode,
    inspector: DisplayObject,
    nodeData: NodeData): HTMLElement {

    console.assert(
        nodeData.childrenValues === undefined ||
        nodeData.childrenValues.length === synthax.children.length)

    const resultHtml = nodeData.result === undefined ?
        [] :
        [box(PrintWizardStyle.Result, [textEl("\u27A1"), displayValue(nodeData.result, inspector)])];


    let code: Node[] = [
        textEl(SPACE.repeat(synthax.prefix.text.length)),
        box(
            PrintWizardStyle.Expression,
            synthax.expression.tokens.flatMap(token => tokenToHtml(token, argumentValue(nodeData.childrenValues, inspector))
            )),
        ...resultHtml,
        ...nodeData.assigns.map(assign => assignToHtml(assign, inspector))
    ]

    return defaultBox(code);
}

/********
**** api
********/



/********
**** helper
********/

function argumentValue(childrenValues: Value[], inspector: DisplayObject): (index: number) => Node[] | undefined {
    return (index: number) => {
        const value = childrenValues[index]
        return value === undefined ? undefined : [textEl(':'), displayValue(value, inspector)];
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
    return box(PrintWizardStyle.Object, [
        box(PrintWizardStyle.Line, [referenceHtml(obj.self)]),
        list(obj.fields.map(fieldHtml))
    ])
}

type Field = {
    identifier: FieldIdentifier,
    value: Value
}

function fieldHtml(field: Field): HTMLElement {
    let item = defaultBox([])

    const inspector = { inspect: (obj : InstanceReference) => { 
        const key = obj.pointer.toString() + '-' + obj.version.toString()

        const dataStore = objectDataCache().data()
        if(dataStore.type==='success'){
            const data = dataStore.payload[key]
            if(data!==undefined){
                item.append(objectHtml(data))
            }
        }
    } }


    item.append(...[
        textEl(field.identifier.name),
        textEl(" = "),
        displayValue(valueFromJson(field.value), inspector)
    ])

    return item
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
                return box(PrintWizardStyle.Object, [
                    box(PrintWizardStyle.TraceItem, [textEl(ref.className.className)]),
                    box(PrintWizardStyle.TraceItem, [textEl('data not loaded')])
                ])
            case 'success':
                const objData: undefined | ObjectData = data.payload[key]
                if (objData === undefined) {
                    return box(PrintWizardStyle.Object, [
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


function itemToHtml(
    ctx: ItemContext,
    assings: Write[],
    executionStep: ExecutionStep,
    formatCode: (synthax: PresentInSourceCode,
        inspector: DisplayObject,
        nodeData: NodeData) => HTMLElement): HTMLElement {


    switch (executionStep.nodeId.kind) {
        case 'absent':
            return empty()

        case 'presentInSourceCode':
            const inspector = new ObjectInspector()

            return box(PrintWizardStyle.TraceItem, [
                inspector.html,
                nodeSythaxToHtml(
                    executionStep.nodeId,
                    ctx.ident,
                    inspector,
                    nodeData(executionStep, assings),
                    formatCode
                )
            ])

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
    Object = 'object',
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

function hide(elem: HTMLElement) {
    elem.style.display = 'none'
}

function show(elem: HTMLElement) {
    elem.style.display = 'block'
}


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

function list(elements: Node[]) {
    let div = document.createElement("ul");
    elements.map(child => {
        let item = document.createElement("li");
        item.appendChild(child)
        div.appendChild(item)
    });
    return div;
}

enum ToggleBoxState {
    ON,
    OFF
}

function offToggleBox(elem: HTMLElement, off: () => void, on: () => void) {
    off()

    let state = ToggleBoxState.OFF

    elem.addEventListener('click', () => {
        switch (state) {
            case ToggleBoxState.OFF:
                on()
                state = ToggleBoxState.ON
                break;
            case ToggleBoxState.ON:
                off()
                state = ToggleBoxState.OFF
                break;
        }
    })
    return elem;
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
