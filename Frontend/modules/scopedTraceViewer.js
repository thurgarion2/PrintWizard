export { fromTraceTree, createViewEvents, update }
import { isControlFlow } from "./events.js";

const createViewEvents = function (start, end) {
    return { start: start, end: end };
}

const fromTraceTree = function (
    traceTree,
    initialContext,
    computeCtx,
    computeViewEvents,
    computeState) {

    return fromTraceTreeHelper(
        traceTree,
        initialContext,
        computeCtx,
        computeViewEvents,
        computeState
    ).child;
}


let eventIndex = {};

const fromTraceTreeHelper = function (
    traceTree,
    context,
    computeCtx,
    computeViewEvents,
    computeState) {

    let scopedTree = new scopedTraceViewer(traceTree, context);
    const event = traceTree.event();
    eventIndex[event.eventId()] = scopedTree;
    
    const newContext = computeCtx(context, event);

    let states = []

    for (const child of traceTree.children()) {
        const res = fromTraceTreeHelper(
            child,
            newContext,
            computeCtx,
            computeViewEvents,
            computeState
        );

        scopedTree.children.push(res.child)
        if (!isControlFlow(event.kind())) {
            states.push(res.state)
        }
    }

    const state = computeState(event, states);
    scopedTree.state = state;
    scopedTree.viewEvents = computeViewEvents(event, state, context);

    return { child: scopedTree, state: state };
}

const recomputeViewEvents = function (scopedTree, computeViewEvents) {
    const event = scopedTree.traceTree.event();
    if(isControlFlow(event.kind())){
        return;
    }

    scopedTree.viewEvents = computeViewEvents(event, scopedTree.state, scopedTree.context);

    for (const child of scopedTree.children) {
        recomputeViewEvents(child, computeViewEvents);
    }
}

const update = function(eventId, computeViewEvents) {
    let scopedTree = eventIndex[eventId];
    recomputeViewEvents(scopedTree, computeViewEvents);
}


//a scoped trace viewer only use information about events in his scope 
//scope mean when there is no control flow change
class scopedTraceViewer {
    constructor(traceTree, context) {
        this.traceTree = traceTree;
        this.state = undefined;
        this.context = context;
        this.viewEvents = undefined;
        this.children = [];
    }

    display() {
        let viewEvents = []
        viewEvents.push(...this.viewEvents.start)

        for (const child of this.children) {
            viewEvents.push(...child.display())
        }

        viewEvents.push(...this.viewEvents.end)

        return viewEvents;
    }
}   