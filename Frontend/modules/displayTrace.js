import {fromCsv } from './eventTree'
import {scopedTrace} from './display'

const fetchEventTrace = function () {
    return fetch('eventTrace')
        .then(response => {
            return response.text()
        })
}

const fetchSourceFormat = function () {
    return fetch('sourceFormat')
        .then(response => {
            return response.json()
        })
}

let traceElement = document.getElementById('trace');
let treeViewer = undefined;

// const showDefault = function(event, state, ctx){
//     switch(event.kind()){
//         case eventKinds.statementEnter:
//         case eventKinds.statementExit:
//             if(event.nodeId()=='foo(i*10| j)'){
//                 return createViewEvents(
//                     [viewEvent(ctx.indent, event.nodeId(), state.assigns, "")], 
//                     []
//                     );
//             }else{
//                 return createViewEvents([], [
//                     viewEventWithAction(
//                         ctx.indent, 
//                         event.nodeId(), 
//                         state.assigns, 
//                         "", 
//                         () => {
//                             update(event.eventId(), showExpr);
//                             traceElement.innerHTML = '';
                            
//                             treeViewer
//                             .display()
//                             .map(htmlElement)
//                             .map(element => {
//                                 traceElement.appendChild(element);
//                                 traceElement.appendChild(brElement());
//                             });
//                         })
//                 ]);
//             }  
//         default:
//             return createViewEvents([], []);     
//     }
// }

// const showExpr = function(event, state, ctx){
//     switch(event.kind()){
//         case eventKinds.statementEnter:
//         case eventKinds.statementExit:
//             if(event.nodeId()=='foo(i*10| j)'){
//                 return createViewEvents(
//                     [viewEvent(ctx.indent, event.nodeId(), state.assigns, "")], 
//                     []
//                     );
//             }else{
//                 return createViewEvents([], [
//                     viewEventWithAction(
//                         ctx.indent, 
//                         event.nodeId(), 
//                         state.assigns, 
//                         "", 
//                         () => {
//                             update(event.eventId(), showDefault);
//                             traceElement.innerHTML = '';
//                             treeViewer
//                             .display()
//                             .map(htmlElement)
//                             .map(element => {
//                                 traceElement.appendChild(element);
//                                 traceElement.appendChild(brElement());
//                             });
//                         })
//                 ]);
//             }
//         case eventKinds.expressionEnter:
//         case eventKinds.expressionExit:            
//             return createViewEvents([], [
//                 viewEvent(ctx.indent, event.nodeId(), state.assigns, event.result())
//             ]);      
//         default:
//             return createViewEvents([], []);     
//     }
// }


Promise.all([fetchEventTrace(), fetchSourceFormat()])
    .then(results => {
        let rawTrace = results[0]
        let format = results[1]

        const traceTree = fromCsv(rawTrace);
        const scopedTraceElement = scopedTrace(traceTree);
        scopedTraceElement.display(traceElement);

        // const traceTree = fromCsv(rawTrace);
        // treeViewer = fromTraceTree(
        //     traceTree,
        //     { indent: 0 },
        //     (ctx, event) => {
        //         return isControlFlow(event.kind()) ? { indent: ctx.indent + 1 } : ctx;
        //     },
        //     showDefault,
        //     (event, states) => {
        //         let state = states.reduce(
        //             (acc, state) => {
        //                 acc.assigns.push(...state.assigns)
        //                 return acc;
        //             },
        //             { assigns: [] }
        //         )
        //         if (event.kind() == eventKinds.update) {
        //             state.assigns.push(event.assignValue());
        //         }
        //         return state;
        //     }
        // );
        

        // treeViewer
        //     .display()
        //     .map(htmlElement)
        //     .map(element => {
        //         traceElement.appendChild(element);
        //         traceElement.appendChild(brElement());
        //     });
    })
    .catch(error => {
        console.error('Error fetching the file:', error);
    });

