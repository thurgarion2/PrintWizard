import {
    ArrayReference,
    DataType,
    Event,
    EventKindTypes,
    ExecutionStep,
    ExecutionStepTypes,
    FieldIdentifier,
    FunctionContext,
    Identifier,
    InstanceReference,
    Literal,
    PresentInSourceCode,
    SimpleExpression,
    Token,
    Value,
    Write
} from './event';
import { ArrayData, ObjectData, searchObject } from './objectStore';
import { EventWithContext } from './treeTransforms';
export {
    aggregateItemState, initialContext, toStepGroup, propagateItemContext
};

/**************
 ********* CONSTANTS
 **************/

const EmojiStart = 0x1F600;
const EmojiEnd = 0x1F64F;
const SPACE = '\u00A0';

/*******************************************************
 **************** tree to Steps Group ******************
 *******************************************************/

function toStepGroup(event: EventWithContext<ItemContext, ItemState>): StepsGroup {


    let groups: StepsGroup[] = []
    let children: HTMLElement[] = []
    let steps: HTMLElement[] = []
    const eventKind = event.kind
    let formatter: any

    switch (eventKind.type) {
        case EventKindTypes.SubStatement:
            formatter = subStatmentCodeToHtml;
            break;
        default:
            formatter = statmentCodeToHtml
            break;
    }

    event.executions().forEach(child => {
        switch (child.type) {
            case 'Event':
                let group: StepsGroup = toStepGroup(child)
                groups.push(group)
                children.push(group.html)
                break;
            case 'ExecutionStep':
                const item = itemToHtml(event.context, event.state.writes, child, formatter)
                children.push(item)
                steps.push(item)
                break;
        }
    })


    switch (eventKind.type) {
        case EventKindTypes.Statement:
            console.assert(steps.length === 1)
            return new StatementGroup(children, steps[0], groups)
        case EventKindTypes.SubStatement:
            return new SubStatementGroup(children, groups)
        case EventKindTypes.Flow:
            switch (eventKind.kind.type) {
                case 'DefaultContext':
                    return new FlowGroup(children, groups)
                case 'FunctionContext':
                    return new FunctionFlowFlowGroup(children,
                        eventKind.kind,
                        event.context.ident,
                        groups)
            }
    }

}



/*******************************************************
 **************** Steps Group ******************
 *******************************************************/

/**************
 ********* Types
 **************/

interface StepsGroup {
    type: StepsGroupType,
    html: HTMLElement,
    collapse: () => void,
    expand: () => void,
    displayMode: () => DisplayMode

}

enum StepsGroupType {
    Statement,
    SubStatement,
    Flow,
    FunctionFlow
}

enum DisplayMode {
    EXPANDED,
    COLLAPSED
}

interface TraceItem {
    html: () => HTMLElement,

}


/**************
 ********* steps group implementations
 **************/

/********
**** Statement Group
********/

class StatementGroup implements StepsGroup {
    type = StepsGroupType.Statement;
    html: HTMLElement;
    groups: StepsGroup[];
    subStatments: SubStatementGroup[];
    mode: DisplayMode;

    constructor(children: HTMLElement[], statementStep: HTMLElement, groups: StepsGroup[]) {
        this.groups = groups;
        this.subStatments = groups.filter(g =>
            g.type === StepsGroupType.SubStatement) as SubStatementGroup[];
        this.mode = DisplayMode.EXPANDED;
        this.html = logicalGroup(children)
        statementStep.addEventListener('click', () => {
            switch (this.mode) {
                case DisplayMode.COLLAPSED:
                    this.expand();
                    break;
                case DisplayMode.EXPANDED:
                    this.collapse()
                    break;
            }
        })

        this.collapse()
    }

    collapse(): void {
        this.groups.forEach(s => s.collapse())
        if (this.mode != DisplayMode.COLLAPSED) {
            this.mode = DisplayMode.COLLAPSED;
        }
    }

    expand(): void {
        if (this.mode != DisplayMode.EXPANDED) {
            this.subStatments.forEach(s => s.expand())
            this.mode = DisplayMode.EXPANDED;
        }
    }

    displayMode(): DisplayMode {
        return this.mode;
    }


}

/********
**** Sub-Statement Group
********/

class SubStatementGroup implements StepsGroup {
    type = StepsGroupType.SubStatement;
    groups: StepsGroup[];
    html: HTMLElement;
    mode: DisplayMode;

    constructor(children: HTMLElement[], groups: StepsGroup[]) {
        this.groups = groups;
        this.mode = DisplayMode.EXPANDED;
        this.html = logicalGroup(children)
        this.collapse()
    }

    collapse(): void {
        if (this.mode != DisplayMode.COLLAPSED) {
            hide(this.html)
            this.groups.forEach(s => s.collapse())
            this.mode = DisplayMode.COLLAPSED;
        }
    }

    expand(): void {
        if (this.mode != DisplayMode.EXPANDED) {
            show(this.html)
            this.mode = DisplayMode.EXPANDED;
        }
    }

    displayMode(): DisplayMode {
        return this.mode;
    }

}

/********
**** simple flow control Group
********/

class FlowGroup implements StepsGroup {
    type = StepsGroupType.Flow;
    groups: StepsGroup[];
    html: HTMLElement;
    mode: DisplayMode;

    constructor(children: HTMLElement[], groups: StepsGroup[]) {
        this.groups = groups;
        this.mode = DisplayMode.EXPANDED;
        this.html = logicalGroup(children)
        this.collapse()
    }

    collapse(): void {
        if (this.mode != DisplayMode.COLLAPSED) {
            this.groups.forEach(s => s.collapse())
            this.mode = DisplayMode.COLLAPSED;
        }
    }

    expand(): void {
        if (this.mode != DisplayMode.EXPANDED) {
            this.mode = DisplayMode.EXPANDED;
        }
    }

    displayMode(): DisplayMode {
        return this.mode;
    }

}

/********
**** function flow control Group
********/

class FunctionFlowFlowGroup implements StepsGroup {
    type = StepsGroupType.FunctionFlow;
    groups: StepsGroup[];
    childGroup: HTMLElement;
    collapsed: HTMLElement;
    html: HTMLElement;
    mode: DisplayMode;

    constructor(children: HTMLElement[], flow: FunctionContext, indentation: number, groups: StepsGroup[]) {
        this.groups = groups;
        this.mode = DisplayMode.EXPANDED;

        const name = textBox(PrintWizardStyle.None, flow.functionName)
        this.collapsed = textBox(PrintWizardStyle.None, "  ...  ")

        const header = box(
            PrintWizardStyle.Flex_Row,
            [widthBox(indentation * SPACES_PER_IDENT_LEVEL + 10),
            box(PrintWizardStyle.Flex_Col, [
                name,
                this.collapsed
            ])]
        )
        this.childGroup = logicalGroup(children)
        this.html = logicalGroup([header, this.childGroup])

        name.addEventListener('click', () => {
            switch (this.mode) {
                case DisplayMode.COLLAPSED:
                    this.expand();
                    break;
                case DisplayMode.EXPANDED:
                    this.collapse()
                    break;
            }
        })

        this.collapse()
    }

    collapse(): void {
        if (this.mode != DisplayMode.COLLAPSED) {
            hide(this.childGroup)
            show(this.collapsed)
            this.groups.forEach(s => s.collapse())
            this.mode = DisplayMode.COLLAPSED;
        }
    }

    expand(): void {
        if (this.mode != DisplayMode.EXPANDED) {
            show(this.childGroup)
            hide(this.collapsed)
            this.mode = DisplayMode.EXPANDED;
        }
    }

    displayMode(): DisplayMode {
        return this.mode;
    }

}


/*******************************************************
 **************** compute context ******************
 *******************************************************/

type ItemContext = {
    ident: number
}

type ItemState = {
    writes: Write[]
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
        case EventKindTypes.Statement:
        case EventKindTypes.SubStatement:

            const steps: SimpleExpression[] = event.executions()
                .filter(x => x.type === 'ExecutionStep' && x.kind.type === ExecutionStepTypes.SimpleExpression)
                .map(x => x.kind) as SimpleExpression[];

            const writes = steps
                .map(s => s.assigns)
                .reduce((acc: Write[], val: Write[]) => acc.concat(val), [])
            return {
                writes: states
                    .map(s => s.writes)
                    .reduce((acc: Write[], val: Write[]) => acc.concat(val), writes)
            }

        default:
            return {
                writes: states
                    .map(s => s.writes)
                    .reduce((acc: Write[], val: Write[]) => acc.concat(val), [])
            }
    }
}


/*******************************************************
 **************** execution step to html ******************
 *******************************************************/

/********
**** types 
********/


type NodeData = {
    result: Value | undefined,
    assigns: Write[],
    childrenValues: Value[]
}

/**************
 ********* item
 **************/

const SPACES_PER_IDENT_LEVEL = 6

function itemToHtml(
    ctx: ItemContext,
    assings: Write[],
    executionStep: ExecutionStep,
    formatCode: (synthax: PresentInSourceCode,
        inspector: DisplayReferenceData,
        nodeData: NodeData) => HTMLElement): HTMLElement {


    switch (executionStep.nodeId.kind) {
        case 'absent':
            return empty()

        case 'presentInSourceCode':
            const inspector = new ObjectInspector()
            const synthax = executionStep.nodeId
            const data = nodeData(executionStep, assings)
            const indentLevel = ctx.ident

            return box(PrintWizardStyle.TraceItem, [
                inspector.html,
                box(PrintWizardStyle.Code, [
                    box(PrintWizardStyle.LineNumbers,
                        lineRange(synthax.startLine, synthax.endLine + 1)
                            .map(l => textBox(PrintWizardStyle.LineNumber, l.toString()))),
                    widthBox(SPACES_PER_IDENT_LEVEL * indentLevel),
                    formatCode(synthax, inspector, data)
                ])
            ])

    }
}

function nodeData(step: ExecutionStep, assigns: Write[]) {
    return {
        result: step.kind.result,
        assigns: assigns,
        childrenValues: step.kind.argVaules === undefined ? [] : step.kind.argVaules
    }
}

/**************
 ********* NodeSourceFormat
 **************/





/********
**** transformation
********/



function statmentCodeToHtml(
    synthax: PresentInSourceCode,
    inspector: DisplayReferenceData,
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
    return logicalGroup(code);
}

function subStatmentCodeToHtml(
    synthax: PresentInSourceCode,
    inspector: DisplayReferenceData,
    nodeData: NodeData): HTMLElement {

    console.assert(
        nodeData.childrenValues === undefined ||
        nodeData.childrenValues.length === synthax.children.length)

    const resultHtml = nodeData.result === undefined ?
        [] :
        [box(PrintWizardStyle.Result, [textEl("\u27A1"), displayValue(nodeData.result, inspector)])];


    let code: Node[] = [
        textEl(SPACE.repeat(3)),
        box(
            PrintWizardStyle.None,
            synthax.expression.tokens.flatMap(token => tokenToHtml(token, argumentValue(nodeData.childrenValues, inspector))
            )),
        ...resultHtml,
        ...nodeData.assigns.map(assign => assignToHtml(assign, inspector))
    ]

    return logicalGroup(code);
}



/********
**** helper
********/

function argumentValue(childrenValues: Value[], inspector: DisplayReferenceData): (index: number) => Node[] | undefined {
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
            return [brEl(), textEl(SPACE.repeat(4))]
        case 'Text':
            return [textEl(token.text)]
        case 'Child':
            const childNode = childToNode(token.childIndex)

            return [textEl(token.text), ... (childNode === undefined ? [empty()] : childNode)]
    }
}


function assignToHtml(assign: Write, inspector: DisplayReferenceData): HTMLElement {
    return box(PrintWizardStyle.Write,
        [...displayIdentifier(assign.identifier, inspector),
        textEl('='),
        displayValue(assign.value as Value, inspector)
        ]
    )
}



/*******************************************************
 **************** object/array data to html ******************
 *******************************************************/

/********
**** types 
********/

type DynamicReference = InstanceReference | ArrayReference

interface DisplayReferenceData {
    inspect: (obj: DynamicReference) => void
}

/**************
 ********* ArrayData
 **************/


class ObjectInspector implements DisplayReferenceData {
    html = box(PrintWizardStyle.Inspector, [])
    displayedRefs: Set<string> = new Set()

    constructor() {
    }

    keyOf(ref: DynamicReference): string {
        return ref.pointer.toString() + '-' + ref.version.toString();
    }

    inspect(ref: DynamicReference) {
        const k = this.keyOf(ref)
        if (!this.displayedRefs.has(k)) {
            this.displayedRefs.add(k)
            this.html.appendChild(this.refDataToHtml(ref))
        }
    }

    refDataToHtml(ref: DynamicReference): HTMLElement {

        const objData = searchObject(ref)

        switch (objData.type) {
            case 'failure':
                const refItem = ref.dataType === DataType.InstanceRef ? referenceHtml(ref) : arrayReferenceHtml(ref)
                return box(PrintWizardStyle.Object, [
                    box(PrintWizardStyle.Line, [refItem]),
                    box(PrintWizardStyle.Line, [textEl('object not found')])
                ])
            case 'success':
                switch (objData.payload.self.dataType) {
                    case DataType.InstanceRef:
                        return objectHtml(objData.payload as ObjectData)
                    case DataType.ArrayReference:
                        return arrayHtml(objData.payload as ArrayData)
                }

        }
    }
}

/**************
 ********* ArrayData
 **************/

function arrayHtml(obj: ArrayData): HTMLElement {
    return box(PrintWizardStyle.Object, [
        box(PrintWizardStyle.Line, [arrayReferenceHtml(obj.self)]),
        list(obj.values.map(v => {
            const item = logicalGroup([])
            item.appendChild(displayValue(v, appendInspector(item)))
            return item;
        }))
    ])
}

/**************
 ********* Object
 **************/


function objectHtml(obj: ObjectData): HTMLElement {
    //only for demo


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
    let item = logicalGroup([])


    item.append(...[
        textEl(field.identifier.name),
        textEl(" = "),
        displayValue(field.value, appendInspector(item))
    ])

    return item
}

function appendInspector(item: HTMLElement): DisplayReferenceData {
    return {
        inspect: (obj: DynamicReference) => {
            const objData = searchObject(obj)
            switch (objData.type) {
                case 'success':
                    switch (objData.payload.self.dataType) {
                        case DataType.InstanceRef:
                            item.append(objectHtml(objData.payload as ObjectData))
                            break;
                        case DataType.ArrayReference:
                            item.append(arrayHtml(objData.payload as ArrayData))
                            break;
                    }

            }
        }
    }
}



/**************
 ********* UI for Identifier
 **************/


function displayIdentifier(ident: Identifier, model: DisplayReferenceData): Node[] {
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

function arrayReferenceHtml(reference: ArrayReference): Node {
    if (Number.isNaN(reference.pointer))
        debugger;
    return textEl('[]$' + emojiUnicode(reference.pointer));
}


/**************
 ********* UI for Value
 **************/

function displayValue(value: Value, actions: DisplayReferenceData): Node {
    switch (value.dataType) {
        case DataType.Literal:
            const repr = value.kind === "null" ? "null" : value.value.toString()
            return textEl(repr);
        case DataType.InstanceRef:
            if (value.className.className === 'Vector2') {
                const res = searchObject(value)

                if(res.type==='success'){
                    const vectorData = res.payload as ObjectData
                    const x = vectorData.fields[0].value as Literal
                    const y = vectorData.fields[1].value as Literal
                    return box(PrintWizardStyle.None, [
                        textEl("Vector["+x.value.toString()+", "+y.value.toString()+"]")
                    ])
                }

            }
            if (value.className.className === 'Boid') {
                const res = searchObject(value)

                if(res.type==='success'){
                    const boidData = res.payload as ObjectData
                    const pos = boidData.fields[0].value as InstanceReference
                    const speed = boidData.fields[1].value as InstanceReference
                    return box(PrintWizardStyle.None, [
                        referenceHtml(value),
                        textEl("[position : "),
                        displayValue(pos, { inspect : (x) => {}}),
                        textEl(",velocity : "),
                        displayValue(speed, { inspect : (x) => {}}),
                        textEl("]")
                    ])
                }

            }
            return clickAction(
                [referenceHtml(value)],
                () => { actions.inspect(value) }
            );
        case DataType.ArrayReference:
            return clickAction(
                [arrayReferenceHtml(value)],
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

    return '';
    //return String.fromCodePoint(codePoint);
}



/*******************************************************
 **************** UI primitive  ******************
 *******************************************************/

/**************
 ********* styles
 **************/

enum PrintWizardStyle {
    None = 'none',
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
    Line = 'line',
    Flex_Row = 'flex_row',
    Flex_Col = 'flex_col'

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


function logicalGroup(elements: Node[]): HTMLElement {
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
