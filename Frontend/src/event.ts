export {
    NodeSourceFormat,
    Event,
    ExecutionStep,
    ExecutionStepTypes,
    SimpleExpression,
    Call,
    VoidCall,
    Value,
    SourceFormatDescription,
    PresentInSourceCode,
    Token,
    parseSourceFormatDescription,
    Identifier,
    Data,
    Write,
    ControlFlow,
    LocalIdentifier,
    Literal,
    FieldIdentifier,
    Result,
    EventKind,
    DataType,
    ArgsValues,
    InstanceReference,
    ArrayReference,
    EventKindTypes,
    parseEventTrace,
    valueFromJson,
    findNodeSynthax,
    FunctionContext
}

import { sourceCodeCache } from "./fetch"


/*******************************************************
 **************** EVENTS ******************
 *******************************************************/


/**************
 ********* event definition
 **************/

// an event is an ordered list of execution steps
// an event represent a logical group of steps

// replace undefined with execution step
type Execution = Event | ExecutionStep


// an event is an ordered list of execution steps
// an event represent a logical group of steps
interface Event {
    type: 'Event',
    // the ordered list of executions steps 
    executions: () => Execution[],
    kind: EventKind,
}

/********
**** different event kinds
********/

type EventKind = Statement | SubStatement | ControlFlow

enum EventKindTypes {
    Statement = "statement",
    SubStatement = "subStatement",
    Flow = "flow"
}

/********
**** events definition
********/

type Statement = {
    type: EventKindTypes.Statement
}

// by default we don't want to display all executions steps
// so there is a sepration between the most outer statement
// and its children
type SubStatement = {
    type: EventKindTypes.SubStatement
}

type ControlFlow = {
    type: EventKindTypes.Flow,
    kind: FunctionContext | DefaultContext
}

type FunctionContext = {
    type: 'FunctionContext',
    functionName: string
}

type DefaultContext = {
    type: 'DefaultContext'
}


/********
**** events constructor
********/

function makeEvent(execution: Execution[], kind: EventKind): Event {
    return {
        type: 'Event',
        executions: () => execution,
        kind: kind,
    };
}

/**************
 ********* execution step definition
 **************/

// an execution step is the evaluation of
// an expression in the code
interface ExecutionStep {
    type: 'ExecutionStep',
    nodeId: NodeSourceFormat,
    kind: ExecutionStepType
}

/********
****  executions step kinds
********/

type ExecutionStepType = SimpleExpression | Call | VoidCall

enum ExecutionStepTypes {
    SimpleExpression = "simpleExpression",
    Call = "call",
    VoidCall = "voidCall"
}

/********
****  executions step specific defintion
********/

type SimpleExpression = {
    type: ExecutionStepTypes.SimpleExpression,
    result: Value,
    assigns: Write[]
}


type Call = {
    type: ExecutionStepTypes.Call,
    result: Value,
    argVaules: Value[]
}

type VoidCall = {
    type: ExecutionStepTypes.VoidCall,
    argVaules: Value[]
}

/********
****  constructor
********/

function makeExecutionStep(nodeId: NodeSourceFormat, kind: ExecutionStepType): ExecutionStep {
    return {
        type: 'ExecutionStep',
        nodeId: nodeId,
        kind: kind
    };
}

/*******************************************************
 **************** parsing event trace ******************
 *******************************************************/



function parseEventTrace(jsonTrace: any[]): Event {
    const steps = parseSteps(jsonTrace)
    const [root, notUsed] = parseEvent(steps, 0)

    return root;
}

/**************
 ********* parsing event tree
 **************/

/********
**** types
********/


/********
**** create Execution Step function
********/

// code is a bit ugly as I have not yet change the json format to reflect 
// the change in the Event format
function parseEvent(labels: RawStep[], startIndex: number): [Event, number] {

    const startStep = labels[startIndex]
    if (startStep.type !== StepType.GroupEvent)
        return unableToParse(startStep)


    let children: Execution[] = []
    const eventId: number = startStep.fields.get('eventId')
    console.assert(typeof eventId === 'number')
    console.assert(startStep.fields.get('pos') === 'start')

    let index = startIndex + 1
    let current = labels[index]

    let callStore: Map<number, ExecutionStep> = new Map();

    while (current.type !== StepType.GroupEvent || current.fields.get('pos') !== 'end') {
        if (current.type === StepType.ExecutionStep) {
            switch (current.fields.get('kind')) {
                case 'expression':

                    children.push(
                        makeExecutionStep(
                            findNodeSynthax(current.fields.get('nodeKey')),
                            {
                                type: ExecutionStepTypes.SimpleExpression,
                                result: valueFromJson(current.fields.get('result')),
                                assigns: current.fields.get('assigns').map(dataFromJson) as Write[]
                            }
                        )
                    )
                    break;
                case 'expressionWithoutReturn':

                    children.push(
                        makeExecutionStep(
                            findNodeSynthax(current.fields.get('nodeKey')),
                            {
                                type: ExecutionStepTypes.SimpleExpression,
                                result: undefined as any,
                                assigns: current.fields.get('assigns').map(dataFromJson) as Write[]
                            }
                        )
                    )
                    break;
                case 'logVoidCall':

                    children.push(makeExecutionStep(
                        findNodeSynthax(current.fields.get('nodeKey')),
                        {
                            type: ExecutionStepTypes.VoidCall,
                            argVaules: current.fields.get('argsValues').map(valueFromJson)
                        }
                    ))

                    break;
                case 'logCall':
                    let step1 = makeExecutionStep(
                        findNodeSynthax(current.fields.get('nodeKey')),
                        {
                            type: ExecutionStepTypes.Call,
                            result: undefined as any,
                            argVaules: current.fields.get('argsValues').map(valueFromJson)
                        }
                    )
                    children.push(step1)
                    callStore.set(current.fields.get('stepId'), step1)
                    break;
                case 'logReturn':
                    console.assert(callStore.has(current.fields.get('stepId')))
                    let step2: ExecutionStep = callStore.get(current.fields.get('stepId')) as ExecutionStep;
                    console.assert(step2.kind.type === ExecutionStepTypes.Call);
                    (step2.kind as Call).result = valueFromJson(current.fields.get('result'))
                    break;
                default:
                    debugger;
                    throw new Error("unspported");
            }
            index = index + 1
            current = labels[index]
            continue;
        }

        if (current.type === StepType.GroupEvent) {
            let [event, endIndex] = parseEvent(labels, index)
            children.push(event)

            index = endIndex + 1
            current = labels[index]
            continue;
        }
    }



    console.assert(current.fields.get('pos') === 'end')
    console.assert(current.fields.get('eventId') === eventId)

    switch (current.fields.get('eventType')) {
        case "controlFlow":
            const flowKind = current.fields.get('kind')

            switch (flowKind['type']) {
                case 'DefaultContext':
                    break;
                case 'FunctionContext':
                    console.assert('functionName' in flowKind)
                    break;
                default:
                    throw new Error("unspported");
            }
            return [makeEvent(children, { type: EventKindTypes.Flow, kind: flowKind }), index];
        case "statement":
            return [makeEvent(children, { type: EventKindTypes.Statement }), index];
        case "subStatement":
            return [makeEvent(children, { type: EventKindTypes.SubStatement }), index];
        default:
            throw new Error("unspported");
    }
}


/********
**** create Execution Step function
********/



function extractFromArray(data: Data[], index: number) {
    if (data.length === 0) {
        return undefined;
    } else {
        return data[index];
    }
}


/**************
 ********* parsing raw label
 **************/

/********
**** types
********/

type RawStep = {
    type: StepType,
    fields: Map<string, any>
}



enum StepType {
    ExecutionStep = "ExecutionStep",
    GroupEvent = "GroupEvent",
}

/********
****  parsing functions
********/

function parseSteps(jsonTrace: any): RawStep[] {
    return jsonTrace["trace"].map(parseStep)
}

const TypeField = 'type'

function parseStep(json: any): RawStep {

    if (!(TypeField in json))
        return unableToParse(json)

    let fields = new Map();
    Object.keys(json)
        .filter(field => field !== TypeField)
        .forEach(field => fields.set(field, json[field]))

    return {
        type: typeFromString(json[TypeField]),
        fields: fields
    }
}



const PosValues = Object.keys(StepType).map(key => (StepType as any)[key])

function typeFromString(stepType: any): StepType {

    if (PosValues.includes(stepType)) {
        return stepType
    } else {
        return unableToParse(stepType);
    }
}



/*******************************************************
 **************** Labels ******************
 *******************************************************/

/**************
 ********* Label pos
 **************/

enum DataType {
    StaticReference = "staticRef",
    InstanceRef = "instanceRef",
    ArrayReference = "arrayRef",
    LocalIdentifier = "localIdentifier",
    FieldIdentifier = "fieldIdentifier",
    Write = "write",
    ArgsValues = "argsValues",
    Result = "result",
    Literal = "literal"
}



/*******************************************************
 **************** Node format ******************
 *******************************************************/



type NodeSourceFormat = PresentInSourceCode | AbsentFromSourceCode

/**************
 ********* types
 **************/

type SourceFormatDescription = {
    sourceFile: SourceFile,
    syntaxNodes: any
}

/********
**** SourceFile definition
********/

type SourceFile = {
    packageName: string,
    fileName: string
}

/********
**** Present In Source Code definition
********/

type PresentInSourceCode = {
    children: string[],
    sourceFile: SourceFile,
    identifier: string,
    startLine: number,
    endLine: number,
    expression: Expression,
    prefix: Text,
    suffix: Text,
    kind: "presentInSourceCode"
}

/********
**** Token definition
********/

type Token = Text | Child | LineStart

type Text = {
    kind: "Text",
    text: string
}

type Expression = {
    kind: "expression",
    tokens: Token[]
}


type Child = {
    kind: "Child",
    childIndex: number,
    text: string
}

type LineStart = {
    kind: "LineStart",
    lineNumber: number
}

/********
**** Absent From SourceCode definition
********/


type AbsentFromSourceCode = {
    kind: "absent",
    identifier: string
}

/**************
 ********* schema
 **************/

/********
**** schema defintions
********/

// we validate that the types have the right form


const SourceFileSchema = jsonSchemaBuilder()
    .stringField("packageName")
    .stringField("fileName")

const TextSchema = jsonSchemaBuilder()
    .constField("kind", "Text")
    .stringField("text")


const ChildSchema = jsonSchemaBuilder()
    .constField("kind", "Child")
    .numberField("childIndex")
    .stringField("text")

const LineStartSchema = jsonSchemaBuilder()
    .constField("kind", "LineStart")

const AbsentFromSourceCodeSchema = jsonSchemaBuilder()
    .constField("kind", "absent")
    .stringField("identifier")

const TokenSchema = sumJsonSchema([TextSchema, ChildSchema, LineStartSchema])

const ExpressionSchema = jsonSchemaBuilder()
    .constField("kind", "expression")
    .array("tokens", TokenSchema)


const PresentInSourceCodeSchema = jsonSchemaBuilder()
    .array("tokens", TokenSchema)
    .array("children", { valid: (s: any) => typeof s === 'string' })
    .objectField("sourceFile", SourceFileSchema)
    .objectField("expression", ExpressionSchema)
    .objectField("prefix", TextSchema)
    .objectField("suffix", TextSchema)
    .stringField("identifier")
    .constField("kind", "presentInSourceCode")

const NodeSourceFormatSchema = sumJsonSchema([PresentInSourceCodeSchema, AbsentFromSourceCodeSchema])

const SourceFormatDescriptionSchema = jsonSchemaBuilder()
    .objectField("sourceFile", SourceFileSchema)
    .objectField("syntaxNodes", {
        valid: (m: any) => {
            return Object.values(m).every(v => NodeSourceFormatSchema.valid(v))
        }
    }

    )


/********
****  Json schema
********/

interface JsonSchema {
    valid: (json: any) => boolean
}

interface JsonSchemaBuilder {
    numberField: (field: string) => JsonSchemaBuilder,
    constField: (field: string, const_: string) => JsonSchemaBuilder,
    array: (field: string, schema: JsonSchema) => JsonSchemaBuilder,
    stringField: (field: string) => JsonSchemaBuilder,
    objectField: (field: string, schema: JsonSchema) => JsonSchemaBuilder,
    valid: (json: any) => boolean

}
//all sum type have a kind field we could use that to dispatch it efficently
function sumJsonSchema(schemas: JsonSchema[]): JsonSchema {
    return {
        valid: (json: any) => schemas.some(s => s.valid(json))
    }
}


//not immutable
function jsonSchemaBuilder(): JsonSchemaBuilder {

    function helper(fields: Map<string, (value: any) => boolean>): JsonSchemaBuilder {
        return {
            numberField: (field: string) => helper(fields.set(field, (value: any) => typeof value === "number")),
            stringField: (field: string) => helper(fields.set(field, (value: any) => typeof value === "string")),
            objectField: (field: string, schema: JsonSchema) => helper(fields.set(field, (value: any) => schema.valid(value))),
            constField: (field: string, const_: string) => helper(fields.set(field, (value: any) => value === const_)),
            array: (field: string, schema: JsonSchema) => helper(fields.set(field, (value: any) => value.every((v: any) => schema.valid(v)))),
            valid: (json: any) => {
                return Object.keys(json).every(field =>
                    fields.has(field) && (fields.get(field) as any)(json[field]))
            }
        }
    }

    return helper(new Map())
}

/********
****  extract Node Source Format
********/

function findNodeSynthax(nodeId: any): NodeSourceFormat {
    if (typeof (nodeId) !== 'string')
        throw new Error('nodeId should be a string')

    const data = sourceCodeCache().data()
    switch (data.type) {
        case 'failure': return {
            kind: "absent",
            identifier: nodeId
        }
        case 'success':
            const nodes = data.payload.syntaxNodes

            if (nodeId in nodes) {
                return nodes[nodeId] as any
            } else {
                return {
                    kind: "absent",
                    identifier: nodeId
                }
            }
    }

}


function parseSourceFormatDescription(json: any): SourceFormatDescription {
    if (!SourceFormatDescriptionSchema.valid(json))
        console.log("invalide source code format file")
    return json;
}

/*******************************************************
 **************** Label data ******************
 *******************************************************/
const typeField = "dataType"


/**************
 ********* Data
 **************/

type Data = Result | Write | ArgsValues | Identifier | Reference

/********
**** types
********/


type Result = {
    dataType: DataType.Result
    value: Value
}
type ArgsValues = {
    dataType: DataType.ArgsValues,
    values: Value[]
}
type Write = {
    dataType: DataType.Write,
    identifier: Identifier,
    value: Value
}

/********
**** parsing
********/

function dataFromJson(json: any): Data {
    switch (readJsonField(typeField, json)) {
        case DataType.StaticReference: return staticRefFromJson(json)
        case DataType.InstanceRef: return instanceRefFromJson(json)
        case DataType.LocalIdentifier: return localIdentifierFromJson(json)
        case DataType.FieldIdentifier: return fieldIdentifierFromJson(json)
        case DataType.ArgsValues:
            return {
                dataType: DataType.ArgsValues,
                values: readJsonField("values", json)
                    .map(valueFromJson)
            }
        case DataType.Result:
            return {
                dataType: DataType.Result,
                value: valueFromJson(readJsonField("result", json))
            }
        case DataType.Write:
            return {
                dataType: DataType.Write,
                identifier: identifierFromJson(readJsonField("identifier", json)),
                value: valueFromJson(readJsonField("value", json))
            }
        default:
            return unableToParse(json)
    }
}

/**************
********* Value
**************/

type Value = Literal | InstanceReference | ArrayReference;

/********
**** types
********/

type Literal = {
    dataType: DataType.Literal,
    kind: string,
    value: number | string | boolean
}


/********
**** parsing
********/
const valueField = "value"

function valueFromJson(json: any): Value {
    switch (readJsonField(typeField, json)) {
        case "null": return { dataType: DataType.Literal, kind: 'null', value: readJsonField(valueField, json) };
        case "int": return { dataType: DataType.Literal, kind: 'int', value: readJsonField(valueField, json) };
        case "long": return { dataType: DataType.Literal, kind: 'long', value: readJsonField(valueField, json) };
        case "bool": return { dataType: DataType.Literal, kind: 'bool', value: readJsonField(valueField, json) };
        case "string": return { dataType: DataType.Literal, kind: 'string', value: readJsonField(valueField, json) };
        case "char": return { dataType: DataType.Literal, kind: 'char', value: readJsonField(valueField, json) };
        case "byte": return { dataType: DataType.Literal, kind: 'byte', value: readJsonField(valueField, json) };
        case "short": return { dataType: DataType.Literal, kind: 'short', value: readJsonField(valueField, json) };
        case "float": return { dataType: DataType.Literal, kind: 'float', value: readJsonField(valueField, json) };
        case "double": return { dataType: DataType.Literal, kind: 'double', value: readJsonField(valueField, json) };
        case DataType.InstanceRef: return instanceRefFromJson(json);
        case DataType.ArrayReference: return arrayRefFromJson(json);
        default: return unableToParse(json);
    }
}

/**************
********* Reference
**************/

type Reference = InstanceReference | StaticReference

/********
**** types
********/

type InstanceReference = {
    dataType: DataType.InstanceRef,
    className: ClassIdentifier,
    pointer: number,
    version: number
}

type StaticReference = {
    dataType: DataType.StaticReference,
    className: ClassIdentifier,
    version: number
}

type ArrayReference = {
    dataType: DataType.ArrayReference,
    elemType: string,
    pointer: number,
    version: number
}

type ClassIdentifier = {
    packageName: string,
    className: string
}

/********
**** parsing
********/
const versionField = "version"
const clazzField = "className"

function refFromJson(json: any): Reference {
    switch (readJsonField(typeField, json)) {
        case DataType.StaticReference:
            return staticRefFromJson(json)
        case DataType.InstanceRef:
            return instanceRefFromJson(json)
        default:
            return unableToParse(json)
    }
}

function staticRefFromJson(json: any): StaticReference {
    switch (readJsonField(typeField, json)) {
        case DataType.StaticReference:
            return {
                dataType: DataType.StaticReference,
                className: readJsonField(clazzField, json),
                version: readJsonField(versionField, json)
            }
        default:
            return unableToParse(json)
    }
}

function instanceRefFromJson(json: any): InstanceReference {
    switch (readJsonField(typeField, json)) {
        case DataType.InstanceRef:
            return {
                dataType: DataType.InstanceRef,
                className: readJsonField(clazzField, json),
                pointer: readJsonField("pointer", json),
                version: readJsonField(versionField, json)
            }
        default:
            return unableToParse(json)
    }
}

function arrayRefFromJson(json :any):ArrayReference {
    switch (readJsonField(typeField, json)) {
        case DataType.ArrayReference:
            return {
                dataType: DataType.ArrayReference,
                pointer: readJsonField("pointer", json),
                version: readJsonField(versionField, json),
                elemType: readJsonField("elemType", json)
            }
        default:
            return unableToParse(json)
    }
}

/**************
********* Identifier
**************/

type Identifier = LocalIdentifier | FieldIdentifier;

/********
**** types
********/

type LocalIdentifier = {
    dataType: DataType.LocalIdentifier
    parentNodeId: string,
    name: string
}


type FieldIdentifier = {
    dataType: DataType.FieldIdentifier,
    owner: Reference,
    name: string
}

/********
**** parsing
********/

function identifierFromJson(json: any): Identifier {
    switch (readJsonField(typeField, json)) {
        case DataType.FieldIdentifier:
            return fieldIdentifierFromJson(json)
        case DataType.LocalIdentifier:
            return localIdentifierFromJson(json)
        default:
            return unableToParse(json)
    }
}

function fieldIdentifierFromJson(json: any): FieldIdentifier {
    switch (readJsonField(typeField, json)) {
        case DataType.FieldIdentifier:
            return {
                dataType: DataType.FieldIdentifier,
                owner: refFromJson(readJsonField("owner", json)),
                name: readJsonField("name", json)
            }
        default:
            return unableToParse(json)
    }
}

function localIdentifierFromJson(json: any): LocalIdentifier {
    switch (readJsonField(typeField, json)) {
        case DataType.LocalIdentifier:
            return {
                dataType: DataType.LocalIdentifier,
                parentNodeId: readJsonField("parent", json),
                name: readJsonField("name", json)
            }

        default:
            return unableToParse(json)
    }
}

/*******************************************************
 **************** Json helpers ******************
 *******************************************************/

// when parsing all types throw exceptions if invalid format
// exceptions should not be catched
// a label data don't have undefined field but may not be present in the label

function unableToParse(json: any): any {
    debugger;
    throw new Error("unable to parse " + json);
}

function readArrayField(index: number, json: any): any {
    if (json.length > index) {
        return json[index]
    } else {
        unableToParse(json)
    }
}

function readJsonField(field: string, json: any): any {
    if (field in json) {
        return json[field]
    } else {
        unableToParse(json)
    }
}