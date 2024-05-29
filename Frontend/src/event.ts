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
    LocalIdentifier,
    Literal,
    FieldIdentifier,
    LabelPos,
    Result,
    EventKind,
    DataType,
    ArgsValues,
    InstanceReference,
    EventKindTypes,
    parseEventTrace,
    valueFromJson,
    findNodeSynthax
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

type EventKind = Statement | SubStatement | ControlFlow | Update

enum EventKindTypes {
    Statement = "statement",
    SubStatement = "subStatement",
    Flow = "flow",
    Update = "update"
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
    type: EventKindTypes.Flow
}

// update are leaf nodes and don't have any execution statement inside
type Update = {
    type: EventKindTypes.Update,
    write: Write
}

/********
**** events constructor
********/

function makeEvent(execution :  Execution[], kind : EventKind): Event {
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
    result: Value
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
    const labels = parseRawLabels(jsonTrace)
    const [root, notUsed] = parseEvent(labels, 0)
    
    return root;
}

/**************
 ********* parsing event tree
 **************/

/********
**** types
********/

type EventState = {
    state: Map<LabelPos, Data[]>,
    nodeId: NodeSourceFormat
}

/********
**** create Execution Step function
********/

// code is a bit ugly as I have not yet change the json format to reflect 
// the change in the Event format
function parseEvent(labels : RawLabel[], startIndex : number):[Event,number]{
    const startLabel = labels[startIndex];
    if(startLabel===undefined)
        debugger;

    if(startLabel.pos===LabelPos.UPDATE){
        console.assert(startLabel.data.length===1)

        return [makeEvent([], {
            type: EventKindTypes.Update,
            write: startLabel.data[0] as Write
        }), startIndex]
    }

    let executions : any[] = []
    let state : EventState = {
        state: new Map(),
        nodeId: startLabel.instanceInfo.nodeId
    }

    state.state.set(startLabel.pos, startLabel.data)

    executions.push(labelToExecutionStep(startLabel.pos, startLabel.eventDescription))

    let index = startIndex+1
    let currentLabel = labels[index]



    while(currentLabel.pos!==LabelPos.END){

        switch(currentLabel.pos){
            case LabelPos.CALL:
                state.state.set(currentLabel.pos, currentLabel.data)
                executions.push(labelToExecutionStep(currentLabel.pos, currentLabel.eventDescription))

                index = index +1
                currentLabel = labels[index]
                break;
            case LabelPos.UPDATE:
            case LabelPos.START:
                let [event, endIndex] : [Event, number] = parseEvent(labels, index)
                index = endIndex +1
                currentLabel = labels[index]
               
                executions.push(event)
        }
    }

    state.state.set(currentLabel.pos, currentLabel.data)
    executions.push(labelToExecutionStep(currentLabel.pos, currentLabel.eventDescription))
    

    executions = executions.map((x : any) => {
        if(x.type==='Event'){
            return x;
        }else{
            return x(state)
        }
    }).filter(x => x!==undefined)

    let eventType : EventKind

    switch(currentLabel.eventDescription.kind){
        case 'expression':
            eventType = {type : EventKindTypes.SubStatement}
            break;
        case 'flow':
            eventType = {type : EventKindTypes.Flow}
            break;
        case 'statement':
            eventType = {type : EventKindTypes.Statement}
            break;
        default:
            debugger;
            throw new Error('unhandled case')
    }


    return [makeEvent(executions, eventType), index]
}


/********
**** create Execution Step function
********/

function labelToExecutionStep(pos: LabelPos, eventDescription: EventType): (state: EventState) => (ExecutionStep | undefined) {
    // should be a big match but not supported in javascript :(
   
    switch (pos) {
        case LabelPos.CALL:
            if (eventDescription.subkind === 'resultCall') {
               
                return (state: EventState) => {
                    let res = state.state.get(LabelPos.END)
                    let args = state.state.get(LabelPos.CALL)

                    console.assert(res.length === 0 || res.length === 1)
                    console.assert(args.length === 0 || args.length === 2)

                   
                    return makeExecutionStep(state.nodeId, {
                        type: ExecutionStepTypes.Call,
                        argVaules: (extractFromArray(args, 1) as ArgsValues).values,
                        result: (extractFromArray(res, 0) as Result).value
                    })
                }
            }

            if (eventDescription.subkind === 'callVoid') {
               
                return (state: EventState) => {
                    let args = state.state.get(LabelPos.CALL)
                    console.assert(args.length === 0 || args.length === 2)
                   
                    return makeExecutionStep(state.nodeId, {
                        type: ExecutionStepTypes.VoidCall,
                        argVaules: (extractFromArray(args, 1) as ArgsValues).values
                    })
                }
            }
            break;
        case LabelPos.END:
            if ((eventDescription.kind === 'expression' || eventDescription.kind === 'statement')
                && eventDescription.subkind === 'simple') {
                
               
                return (state: EventState) => {
                    let res = state.state.get(LabelPos.END)

                    console.assert(res.length === 0 || res.length === 1)
                   
                    return makeExecutionStep(state.nodeId, {
                        type: ExecutionStepTypes.SimpleExpression,
                        result: (extractFromArray(res, 0) as Result).value
                    })
                }
            }
        case LabelPos.UPDATE:
        case LabelPos.START:
            return (state: EventState) => undefined
    }

    throw new Error('case not covered');
}

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

type RawLabel = {
    pos: LabelPos,
    instanceInfo: InstanceInfo,
    eventDescription: EventType,
    data: Data[]
}

type EventType = {
    kind: string,
    subkind: string
}

type EventId = { id: number }

type InstanceInfo = {
    eventId: EventId,
    nodeId: NodeSourceFormat
}

enum LabelPos {
    START = "start",
    UPDATE = "update",
    END = "end",
    CALL = "call"
}

/********
****  parsing functions
********/

function parseRawLabels(jsonTrace: any): RawLabel[] {
    return jsonTrace["trace"].map(parseRawLabel)
}

const startIndexData = 5

function parseRawLabel(json: any): RawLabel {

    return {
        pos: labelPosFromJson(json),
        instanceInfo: instanceInfoFromJson(json),
        eventDescription: eventTypeFromJson(json),
        data: json.slice(startIndexData, json.length).map(dataFromJson)
    }
}

function eventTypeFromJson(json: any[]): EventType {
    const kind = readArrayField(4, json)
    const subkind = readArrayField(3, json)
    const t = { kind: kind, subkind: subkind }
    return t
}

function instanceInfoFromJson(json: any[]): InstanceInfo {
    const eventId = { id: Number(readArrayField(1, json)) }
    const nodeId = readArrayField(2, json)

    return {
        eventId: eventId,
        nodeId: findNodeSynthax(nodeId)
    }
}

const PosValues = Object.keys(LabelPos).map(key => (LabelPos as any)[key])

function labelPosFromJson(json: any): LabelPos {
    const labelPos = json[0]
    if (PosValues.includes(labelPos)) {
        return labelPos
    } else {
        return unableToParse(json)
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
    tokens: Token[],
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

type Value = Literal | InstanceReference;

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
        case "instanceRef": return instanceRefFromJson(json);
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