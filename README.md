# manifest-edn

[![Clojars Project](https://img.shields.io/clojars/v/io.github.abogoyavlensky/manifest-edn.svg)](https://clojars.org/io.github.abogoyavlensky/manifest-edn)
[![cljdoc badge](https://cljdoc.org/badge/io.github.abogoyavlensky/manifest-edn)](https://cljdoc.org/jump/release/io.github.abogoyavlensky/manifest-edn)
[![CI](https://github.com/abogoyavlensky/manifest-edn/actions/workflows/snapshot.yaml/badge.svg?branch=master)](https://github.com/abogoyavlensky/manifest-edn/actions/workflows/snapshot.yaml)

A small Clojure/Babashka library for hashing static assets.

## Overview

`manifest-edn` is a utility library designed to solve the problem of cache busting for static assets in Clojure/Babashka applications. It provides functionality to:

1. Fetch remote assets and save them locally
2. Hash static assets by appending content-based MD5 hashes to filenames
3. Generate a manifest file mapping original filenames to their hashed versions
4. Retrieve asset paths at runtime using the manifest

This approach ensures that when asset content changes, the filename also changes, forcing browsers to download the new version instead of using a cached copy.

## Usage

### Installation

Add the dependency to your project:

```clojure
;; deps.edn
{:deps {io.github.abogoyavlensky/manifest-edn {:mvn/version "LATEST"}}}

;; or Leiningen/Boot
[io.github.abogoyavlensky/manifest-edn "0.1.0"]
```

### Fetching Remote Assets
Use `fetch-assets!` to download remote assets and save them locally:

```clojure
(require '[manifest-edn.core :as manifest])

(manifest/fetch-assets! [{:url "https://cdn.example.com/jquery.min.js" 
                          :filepath "js/jquery.min.js"}
                         {:url "https://cdn.example.com/styles.css" 
                          :filepath "css/styles.css"}])
 ```

This will download the specified files and save them to the `resources/public` directory.
As a second argument you can specify a path to a directory where you want to save the assets:
```clojure
(manifest/fetch-assets! ...
                        {:resources-dir "custom-resources"
                        :public-dir "custom-public"})
```

### Hashing Assets
Use `hash-assets!` to process all static assets in your public directory:

```clojure
;; With default options
(manifest/hash-assets!)

;; Or with custom options
(manifest/hash-assets!
  {:resource-dir "custom-resources"
   :public-dir "static"
   :resource-dir-target "dist"})
 ```

This will:

1. Find all files in your public directory
2. Create hashed versions (e.g., `app.js` -> `app.a1b2c3d4.js`)
3. Save them to the target directory
4. Generate a manifest file mapping original paths to hashed paths

### Referencing Assets in Templates
Use the asset function to get the correct path to a hashed asset:

```clojure
(require '[manifest-edn.core :as manifest])

;; In your HTML templates
(def js-path (manifest/asset "js/app.js"))
;; => "/assets/js/app.a1b2c3d4.js"

;; With custom asset prefix
(def css-path (manifest/asset "static" "css/styles.css"))
;; => "/static/css/styles.e5f6g7h8.css"
 ```

The function will return the hashed path if available, or fall back to the original path if not found in the manifest.
This is useful, for example, in development.

### Default Configuration
- Resources directory: `"resources"`
- Public directory: `"public"`
- Hashed resources directory: `"resources-hashed"` - this directory will be created automatically and supposed to be **ignored by git**
- Manifest file: `"manifest.edn"` - this file will be created automatically
- Asset prefix: `"assets"`

## Development

### Requirements
Install Java, Clojure and Babashka manually or via [mise](https://mise.jdx.dev/):

```shell
mise install
```

*Note: Check versions in `.mise.toml` file.*

### Manage project

All management tasks:

```shell
bb tasks
The following tasks are available:

deps            Install all deps
fmt-check       Check code formatting
fmt             Fix code formatting
lint-init       Import linting configs
lint            Linting project's code
test            Run tests
outdated-check  Check outdated Clojure deps versions
outdated        Upgrade outdated Clojure deps versions
check           Run all code checks and tests
install-snapshot Install snapshot version locally
install         Install version locally
deploy-snapshot Deploy snapshot version to Clojars
deploy-release  Deploy release version to Clojars
release         Create and push git tag for release
```

## Build and publish

### Install locally

```shell
bb install
```

### Deploy to Clojars from local machine

**Note:** Publishing to Clojars requires `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` environment variables.

Deploy snapshot version:

```shell
bb deploy-snapshot
```

Deploy release version:

```shell
bb deploy-release
```

### Deploy to Clojars from Github Actions

Set up following secrets for Actions:

- `CLOJARS_USERNAME`
- `CLOJARS_PASSWORD`

Then you will be able to push to master branch to deploy snapshot version automatically.

Once you decide to publish release you just need to bump version at deps.edn:

`:aliases -> :build -> :exec-args -> :version -> "0.1.1`

and create a git tag with this version. There is a shortcut command for this:

```shell
bb release
```

This command will create a git tag with the latest version from deps.edn and push it to git repository.
Github Actions will automatically deploy a release version to Clojars.

## License
MIT License
Copyright (c) 2025 Andrey Bogoyavlenskiy
