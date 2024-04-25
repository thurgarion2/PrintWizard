import { fromJson } from './eventTree'
import {traceElement, hbox, vbox, objectElement} from './display'

const fetchEventTrace = function () {
    return fetch('eventTrace')
        .then(response => {
            return response.json()
        })
}

const fetchSourceFormat = function () {
    return fetch('sourceFormat')
        .then(response => {
            return response.json()
        })
}

let injectNode = document.getElementById('trace');
let treeViewer = undefined;


Promise.all([fetchEventTrace(), fetchSourceFormat()])
    .then(results => {
        let rawTrace = results[0]
        let format = results[1]


        const traceTree = fromJson(rawTrace);
    
        const painter = {
            paint : (node) => injectNode.appendChild(node)
        };

        let objects = [];
        const exploreObject = vbox(() => {
            return objects.map(objectElement);
        })

        const traceNode = traceElement(traceTree, {addObject : (obj) => {
            objects.unshift(obj);
            exploreObject.update();
        }});

        const app = hbox(() => {return [traceNode, exploreObject]})
        app.display(painter);

    })
    .catch(error => {
        console.error('Error fetching the file:', error);
    });

