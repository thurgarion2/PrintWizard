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


Promise.all([fetchEventTrace(), fetchSourceFormat()])
    .then(results => {
        let rawTrace = results[0]
        let format = results[1]

        const traceTree = fromCsv(rawTrace);
        const scopedTraceElement = scopedTrace(traceTree, traceElement);
        scopedTraceElement.display();

    })
    .catch(error => {
        console.error('Error fetching the file:', error);
    });

