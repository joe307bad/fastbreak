// Turn a pretty URL into the key the export actually wrote.
//
// `web/` builds with `trailingSlash: true`, so every route is written as
// `<route>/index.html`. The S3 *website* endpoint would resolve that itself,
// but it only speaks HTTP and needs a public bucket; this distribution reads
// the bucket over its REST endpoint through an OAC instead, and the REST
// endpoint has no notion of a directory index -- `GET /nba/` asks for the key
// `nba/`, which does not exist.
//
// So the mapping happens here, at the edge, before the origin is asked:
//   /nba/              -> /nba/index.html
//   /nba               -> /nba/index.html
//   /_next/static/x.js  (has an extension; left alone)
//
// Written to ES5 on purpose. The CloudFront Functions runtime is not a browser
// and not Node; `endsWith` and `includes` are the kind of thing that works
// until it is deployed, so neither is used.
function handler(event) {
    var request = event.request;
    var uri = request.uri;

    if (uri.charAt(uri.length - 1) === '/') {
        request.uri = uri + 'index.html';
        return request;
    }

    // A dot in the last segment means the request is for a file the export
    // wrote by name -- an asset, an RSC payload, an image. Anything else is a
    // route, and routes are directories.
    var lastSegment = uri.substring(uri.lastIndexOf('/') + 1);
    if (lastSegment.indexOf('.') === -1) {
        request.uri = uri + '/index.html';
    }

    return request;
}
