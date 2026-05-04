# Tools

Place `embabel-codegen.jar` in this directory for local template use:

```bash
cp ../spring-crablet/embabel-codegen/target/embabel-codegen.jar tools/embabel-codegen.jar
```

Or run template Make targets with:

```bash
make plan CRABLET_CODEGEN_JAR=/path/to/embabel-codegen.jar
```

`diagram-preview.js` and `event-model-renderer.js` support:

```bash
make diagram-preview
```

That command writes `diagram-preview.html` from the app's `event-model.yaml`. It requires Node.js
and `js-yaml`; install the package locally with:

```bash
npm install --prefix tools --silent
```
