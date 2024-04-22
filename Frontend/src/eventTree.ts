export {
    fromCsv,
    EVENTTREE,
    POSITION,
    TRACEINDEX,
    makeScopedStateTree
};
import {
    EVENT,
    parseEvent,
    labelEventId,
    EventKinds,
    isSingleLabel
} from "./event";


/*******************************************************
 **************** Event Trees ******************
 *******************************************************/


/**************
 ********* Types
 **************/

type EVENTTREE = {
    type: 'EVENTTREE',
    children: () => EVENTTREE[],
    event: EVENT,
    linearize: () => TRACEINDEX[]
}

type DEFAULT_EVENTTREE = {
    type: 'EVENTTREE',
    subtype : 'DEFAULT',
    children: () => DEFAULT_EVENTTREE[],
    event: EVENT,
    linearize: () => TRACEINDEX[]
}

type SCOPEDSTATE_TREE<STATE,CONTEXT> = {
    type: 'EVENTTREE',
    subtype : 'SCOPEDSTATE',
    children: () => SCOPEDSTATE_TREE<STATE,CONTEXT>[],
    event: EVENT,
    state : [STATE, CONTEXT, EVENT],
    linearize: () => [STATE, CONTEXT, TRACEINDEX][]
}

enum POSITION {
    START,
    END,
    SINGLE
}

type TRACEINDEX = {
    type: 'TRACEINDEX',
    event: EVENT,
    pos: POSITION
}

/**************
 ********* constructors
 **************/

const makeTraceIndex = function (event: EVENT, pos: POSITION): TRACEINDEX {
    return {
        type: 'TRACEINDEX',
        event: event,
        pos: pos
    }
}

/********
 **** default event tree
 ********/

const makeDefaultEventTree = function (children: DEFAULT_EVENTTREE[], event: EVENT): DEFAULT_EVENTTREE {
    return {
        type: 'EVENTTREE',
        subtype: 'DEFAULT',
        children: () => children,
        event: event,
        linearize: () => {
            if (event.kind === EventKinds.Update) {
                return [makeTraceIndex(event, POSITION.SINGLE)];
            } else {
                let arr = [makeTraceIndex(event, POSITION.START)]
                children.map(child => arr.push(...child.linearize()))
                arr.push(makeTraceIndex(event, POSITION.END))
                return arr;
            }
        }
    };
}

/********
 **** scopedStateTree
 ********/

function makeScopedStateTree<STATE, CONTEXT>(tree : EVENTTREE,
    context : CONTEXT,
    parentState : (event : EVENT, state : STATE[]) => STATE,
    childrenContext : (event : EVENT, ctx :CONTEXT) => CONTEXT
    ): SCOPEDSTATE_TREE<STATE, CONTEXT>{
    const newContext = childrenContext(tree.event, context);

    let children : SCOPEDSTATE_TREE<STATE, CONTEXT>[] = [];
    let states = [];

    for(const child of tree.children()){
        const scopedChild = makeScopedStateTree(
            child,
            newContext,
            parentState,
            childrenContext
        );

        children.push(scopedChild);    

        const [state, ctx, event] = scopedChild.state;
        if(event.kind!==EventKinds.Flow){
            states.push(state);
        }
    }

    const event = tree.event
    const state = parentState(tree.event, states)

    return {
        type: 'EVENTTREE',
        subtype : 'SCOPEDSTATE',
        children: () => children,
        event: tree.event,
        state : [state, context, event],
        linearize: () => {
            if (event.kind === EventKinds.Update) {
                return [[state, context, makeTraceIndex(event, POSITION.SINGLE)]];
            } else {
                let arr : [STATE, CONTEXT, TRACEINDEX][] = [[state, context, makeTraceIndex(event, POSITION.START)]]
                children.map(child => arr.push(...child.linearize()))
                arr.push([state, context, makeTraceIndex(event, POSITION.END)])
                return arr;
            }
        }
    };
}



/**************
 ********* util functions
 **************/

const fromCsv = function (csv: string): EVENTTREE {
    const events = csv
        .split('\n')
        .filter(label => label.length > 0)
        .map(label => label.split(', '));

    return fromTrace(events, 0)[0];
}

const fromTrace = function (trace: string[][], startIndex: number): [DEFAULT_EVENTTREE, number] {
    const startLabel = trace[startIndex]
    const eventId = labelEventId(startLabel)

    if(isSingleLabel(startLabel)){
        return [makeDefaultEventTree([], parseEvent(startLabel, startLabel)), startIndex];
    }

    console.assert(startLabel[1].endsWith('Enter'));

    let children = []
    let index = startIndex + 1

    while (labelEventId(trace[index]) !== eventId) {
        const [child, end] = fromTrace(trace, index);
        children.push(child);
        index = end + 1;
    }

    const endLabel = trace[index];
    const event = parseEvent(startLabel, endLabel);
    return [makeDefaultEventTree(children, event), index];
}

