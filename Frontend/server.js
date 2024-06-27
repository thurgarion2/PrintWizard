const http = require("http");
const fs = require('fs').promises;

const host = 'localhost';
const port = 8000;



const index = function (req, res) {

    fs.readFile('./index.html')
        .then(contents => {
            res.setHeader("Content-Type", "text/html");
            res.writeHead(200);
            res.end(contents);
        })
        .catch(err => {
            res.writeHead(500);
            res.end(err);
            return;
        });
};

const css = function (req, res) {

    fs.readFile('./printwizard.css')
        .then(contents => {
            res.setHeader("Content-Type", "text/css");
            res.writeHead(200);
            res.end(contents);
        })
        .catch(err => {
            res.writeHead(500);
            res.end(err);
            return;
        });
};

const exposeFile = function (req, res, path) {
    fs.readFile(path)
        .then(contents => {
            res.setHeader("Content-Type", "application/json");
            res.writeHead(200);
            res.end(contents);
        })
        .catch(err => {
            console.log(err)
            res.writeHead(500);
            res.end(err);
            return;
        });
};


const exposeModule = function (req, res, path) {

    fs.readFile(path)
        .then(contents => {
            res.setHeader("Content-Type", "application/javascript");
            res.writeHead(200);
            res.end(contents);
        })
        .catch(err => {
            res.writeHead(500);
            res.end(err);
            return;
        });
};

const ProjectFile = './../IntegrationTest/examples/Boids'
const requestListener = function (req, res) {
    switch (req.url) {
        case "/":
            index(req, res)
            break
        case "/printwizard":
            css(req, res)
            break
        case "/eventTrace":
            exposeFile(req, res, ProjectFile+'/eventTrace.json')
            break
        case "/sourceFormat":
            exposeFile(req, res, ProjectFile+'/source_format.json')
            break
        case "/objectData":
            exposeFile(req, res, ProjectFile+'/objectData.json')
            break
        case "/displayTrace":
            exposeModule(req, res, './modules/displayTrace.js');
            break;
        case "/event":
            exposeModule(req, res, './build/event.js');
            break;
        case "/treeTransforms":
            exposeModule(req, res, './build/treeTransforms.js');
            break;
        case "/objectStore":
            exposeModule(req, res, './build/objectStore.js');
            break;
        case "/display":
            exposeModule(req, res, './build/display.js');
            break;
        case "/fetch":
            exposeModule(req, res, './build/fetch.js');
            break;
        default:
            console.log(req.url);
            console.assert(false);
    }
}


const server = http.createServer(requestListener);
server.listen(port, host, () => {
    console.log(`Server is running on http://${host}:${port}`);
});