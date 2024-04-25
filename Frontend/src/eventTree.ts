export {
    fromJson,
    RootEventTree,
    POSITION,
    TRACEINDEX,
    TraceView,
    TraceIterator,
    rank,
    predecessor,
    successor,
    scopedStateFromEventTree,
    traceViewFromScopedState
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



interface EVENTTREE {
    type: 'EVENTTREE',
    children: () => EVENTTREE[],
    event: EVENT
}

// the root span all the trace
interface RootEventTree {
    type: 'RootEventTree',
    children: () => EVENTTREE[],
    event: EVENT
} 

interface SCOPEDSTATE_TREE<STATE,CONTEXT> {
    type: 'SCOPEDSTATE',
    children: () => SCOPEDSTATE_TREE<STATE,CONTEXT>[],
    event: EVENT,
    state : [STATE, CONTEXT, EVENT]
}

// the root span all the trace
interface RootScopedStateTree<STATE,CONTEXT> {
    type: 'RootScopedStateTree',
    children: () => SCOPEDSTATE_TREE<STATE,CONTEXT>[],
    event: EVENT,
    state : [STATE, CONTEXT, EVENT]
} 

// a subset of a trace that has kept the same ordering
interface TraceView<T> {
    start : TraceIterator<T>
    end : TraceIterator<T>
}

interface TraceIterator<T> {
    predecessor : () => TraceIterator<T> | undefined
    successor : () => TraceIterator<T> | undefined
    element : [TRACEINDEX, T]
}

enum POSITION {
    START,
    END,
    SINGLE
}

//the set of trace index is ordinal
//there is a one to one matching with the original trace
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

function makeEventTree(children: EVENTTREE[], event: EVENT): EVENTTREE {
    return {
        type: 'EVENTTREE',
        children: () => children,
        event: event
    };
}

/********
 **** scopedStateTree
 ********/

function makeScopedStateTree<STATE, CONTEXT>(tree : EVENTTREE | RootEventTree,
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
        type: 'SCOPEDSTATE',
        children: () => children,
        event: tree.event,
        state : [state, context, event]
    };
}

/**************
 ********* types api
 **************/


/********
 **** TRACEINDEX
 ********/


function rank(index : TRACEINDEX): number {
    return 0;
}

function predecessor(index : TRACEINDEX): TRACEINDEX {
    return index;
}

function successor(index : TRACEINDEX): TRACEINDEX {
    return index;
}

/********
 **** SCOPEDSTATE_TREE
 ********/

function scopedStateFromEventTree<STATE, CONTEXT>(tree : RootEventTree,
    context : CONTEXT,
    parentState : (event : EVENT, state : STATE[]) => STATE,
    childrenContext : (event : EVENT, ctx :CONTEXT) => CONTEXT
    ):RootScopedStateTree<STATE,CONTEXT> {
    
    const root = makeScopedStateTree<STATE, CONTEXT>(tree,
        context,
        parentState,
        childrenContext);
    return  {
        type: 'RootScopedStateTree',
        children: root.children,
        event: root.event,
        state : root.state
    };

}



function traceViewFromScopedState<STATE,CONTEXT>(tree : RootScopedStateTree<STATE,CONTEXT>) 
    : TraceView<[STATE,CONTEXT]>{
        const arr = scopedStateToArray(tree);
        return {
            start : iterForScopedState(arr, 0),
            end : iterForScopedState(arr, arr.length-1)
        } ;
}

function scopedStateToArray<STATE,CONTEXT>(tree : RootScopedStateTree<STATE,CONTEXT> | SCOPEDSTATE_TREE<STATE,CONTEXT>)
    :[TRACEINDEX, STATE,CONTEXT][]{
        const event = tree.event
        const [state, ctx] = tree.state
        switch(event.kind){
            case EventKinds.Update:
                return [[makeTraceIndex(event, POSITION.SINGLE), state, ctx]];
            default:
                let arr :[TRACEINDEX, STATE,CONTEXT][] = [[makeTraceIndex(event, POSITION.START), state, ctx]];
                for(const child of tree.children()){
                    arr.push(...scopedStateToArray(child));
                }
                arr.push([makeTraceIndex(event, POSITION.END), state, ctx]);
                return arr;
        }

}

function iterForScopedState<STATE,CONTEXT>(trace : [TRACEINDEX, STATE,CONTEXT][],
    index : number):TraceIterator<[STATE,CONTEXT]>{
        const [idx, state, ctx] = trace[index];

        return {
            predecessor : () => {
                if(index>0){
                    return iterForScopedState(trace, index-1);
                }else{
                    return undefined;
                }
            },
            successor : () => {
                if(index+1<trace.length){
                    return iterForScopedState(trace, index+1);
                }else{
                    return undefined;
                }
            },
            element : [idx, [state, ctx]]
        };
    }


/**************
 ********* util functions
 **************/

function fromJson(trace: any): RootEventTree {
    const events : any[][]  = trace["trace"];

    const root = fromTrace(events, 0)[0];
    return {
        type: 'RootEventTree',
        children: root.children,
        event: root.event
    } ;
}

const fromTrace = function (trace: any[][], startIndex: number): [EVENTTREE, number] {
    const startLabel = trace[startIndex]
    const eventId = labelEventId(startLabel)

    if(isSingleLabel(startLabel)){
        return [makeEventTree([], parseEvent(startLabel, startLabel)), startIndex];
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
    return [makeEventTree(children, event), index];
}

