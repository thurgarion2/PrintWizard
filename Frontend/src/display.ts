import {
    RootEventTree,
    POSITION,
    scopedStateFromEventTree,
    traceViewFromScopedState,
    TraceIterator,
    TRACEINDEX
} from './eventTree'
import { ASSIGN, EVENT, EventKinds, nodeIdRepr } from './event';
export {
    scopedTrace
};


/**************
 ********* Types
 **************/

interface UIElement {
    //should only append as one of its children
    display: (element: HTMLElement) => void
}

interface UITrace extends UIElement {

}

interface UIEventElement extends UIElement {

}

/**************
 ********* UI for trace
 **************/

function scopedTrace(tree: RootEventTree, element: HTMLElement): UITrace {
    const scopedStateTree = scopedStateFromEventTree<ASSIGNSTATE, IDENTCONTEXT>(
        tree,
        zero(),
        updateState,
        updateContext
    );

    const traceView = traceViewFromScopedState(scopedStateTree);
    const windowSize = 40;
    let startWindow = traceView.start;



    const display = () => {
        element.innerHTML = '';

        let it: TraceIterator<[ASSIGNSTATE, IDENTCONTEXT]> | undefined = startWindow;
        let count = 0;

        while (count < windowSize && it !== undefined) {
            const [eventIndex, [state, ctx]] = it.element;
            switch (eventIndex.pos) {
                case POSITION.START:
                    break;
                default:
                    count += 1;
                    eventElement(ctx.ident, nodeIdRepr(eventIndex.event.nodeId))
                        .display(element);
            }
            it = it.successor()
        }

    }
   
    document.addEventListener("keypress", (event: KeyboardEvent) => {
        const keyName = event.key;
        switch (keyName) {
            case 's':
                const next = nextDisplayEvent(startWindow);
                startWindow = next === undefined ? startWindow : next;
                break;
            case 'w':
                const prev = prevDisplayEvent(startWindow);
                startWindow = prev === undefined ? startWindow : prev;
                break;
        }
        display()

    });


    return {
        display: display
    };
}

function prevDisplayEvent(iter : TraceIterator<[ASSIGNSTATE, IDENTCONTEXT]>):TraceIterator<[ASSIGNSTATE, IDENTCONTEXT]>|undefined{
    let it : undefined | TraceIterator<[ASSIGNSTATE, IDENTCONTEXT]>  = iter.predecessor();
    while(it!==undefined){
        const [idx, [assign, ident]] = it.element
        if(isDisplayed(idx, assign, ident)){
            return it;
        }
        it = it.predecessor();
    }
    return it;
}

function nextDisplayEvent(iter : TraceIterator<[ASSIGNSTATE, IDENTCONTEXT]>):TraceIterator<[ASSIGNSTATE, IDENTCONTEXT]>|undefined{
    
    let it : undefined | TraceIterator<[ASSIGNSTATE, IDENTCONTEXT]>  = iter.successor();
    while(it!==undefined){
        const [idx, [assign, ident]] = it.element
        if(isDisplayed(idx, assign, ident)){
            return it;
        }
        it = it.successor();
    }
    return it;
}

function isDisplayed(idx : TRACEINDEX, assign : ASSIGNSTATE, ident : IDENTCONTEXT):boolean{
    switch (idx.pos) {
        case POSITION.START:
            return false;
        default:
           return true;
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
    return {
        assigns: states
            .map(s => s.assigns)
            .reduce((acc: ASSIGN[], val: ASSIGN[]) => acc.concat(val), [])
    }
}

/**************
 ********* UI for Event
 **************/


function eventElement(identation: number, text: string): UIEventElement {
    return {
        display: (htmlElement: HTMLElement) => {
            const element = textEl(identationRepr(identation) + text);
            htmlElement.appendChild(element);
            htmlElement.appendChild(brEl());
        }
    };
}


/********
 **** formatting utils for view elements
 ********/

// const joinAssigns = function (assigns) {
//     return assigns
//         .map(assign => varName(assign) + '=' + value(assign))
//         .join(', ');
// }

let storeIdent: Map<number, string> = new Map<number, string>([
    [0, ""]
]);

function identationRepr(ident: number): string {
    console.assert(ident >= 0)

    if (!storeIdent.has(ident)) {
        storeIdent.set(ident, '\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0' + identationRepr(ident - 1));
    }
    const res = storeIdent.get(ident);
    return res === undefined ? '' : res;
}

/**************
 ********* UI utils
 **************/

function clickAction(elements: HTMLElement[], action: () => void): HTMLElement {
    let span = spanEl(elements, undefined);
    span.addEventListener('click', action)
    return span;
}

function spanEl(elements: HTMLElement[] | Text[], css: string | undefined): HTMLElement {
    let span = document.createElement("span");
    elements.map(child => span.appendChild(child));

    if (css !== undefined) {
        span.className = css;
    }
    return span;
}


function styleText(prefix: string, text: string, cssClass: string): HTMLElement {
    return spanEl([textEl(prefix + text)], cssClass);
}

function brEl(): HTMLElement {
    return document.createElement("br");
}


function textEl(text: string): Text {
    return document.createTextNode(text);
}
