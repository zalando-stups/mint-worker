# mint-worker

[![Build Status](https://travis-ci.org/zalando-stups/mint-worker.svg?branch=master)](https://travis-ci.org/zalando-stups/mint-worker)
[![Coverage Status](https://coveralls.io/repos/zalando-stups/mint-worker/badge.svg?branch=master&service=github)](https://coveralls.io/github/zalando-stups/mint-worker?branch=master)

mint is the secret rotator and distributor for the STUPS ecosystem.

## Download

Releases are pushed as Docker images in the [public Docker registry](https://registry.hub.docker.com/u/stups/):

You can run mint by starting it with Docker:

    $ docker run -it stups/mint-worker

## Requirements

* PostgreSQL 9.4+

## Configuration

Configuration is provided via environment variables during start.

### Mint Worker

Variable                | Mandatory? | Default                 | Description
----------------------- | ---------- | ----------------------- | -----------
OAUTH2_ACCESS_TOKEN_URL | yes        |                         | URL of the `/access_token` endpoint of the authorization server (to retrieve OAuth tokens)
JOBS_KIO_URL            | yes        |                         | URL to [Kio](https://github.com/zalando-stups/kio). Used to verify applications.
JOBS_SERVICE_USER_URL   | yes        |                         | URL to Service User API
JOBS_MINT_STORAGE_URL   | yes        |                         | URL to Mint storage
JOBS_ESSENTIALS_URL     | yes        |                         | URL of [essentials](https://github.com/zalando-stups/essentials). Used to verify scopes.
JOBS_MINT_COWORKER_URL  | no         |                         | URL of [mint-coworker proxy](deps/mint-coworker.yaml). Used for proxying bucket writes to GCS.
JOBS_MAX_S3_ERRORS      | no         | 10                      | At which point mint-worker pauses password/client rotation for applications
JOBS_ETCD_LOCK_URL      | no         |                         | etcd key URL for locking
JOBS_ETCD_LOCK_TTL      | no         | 500                     | etcd TTL for locking

Example:

~~~
$ docker run -it \
    -e OAUTH2_ACCESS_TOKEN_URL="https://auth-example.com/access_token" \
    -e JOBS_KIO_URL="https://kio.example.com" \
    -e JOBS_SERVICE_USER_URL="https://service-user.example.com" \
    -e JOBS_MINT_STORAGE_URL="https://mint.example.com" \
    -e JOBS_ESSENTIALS_URL="https://essentials.example.com" \
    stups/mint-storage
~~~

## Building

    $ lein do uberjar, scm-source, docker build

## Releasing

    $ lein release :minor

## Developing

Mint embeds the [reloaded](http://thinkrelevance.com/blog/2013/06/04/clojure-workflow-reloaded) workflow for interactive
development:

    $ lein repl
    user=> (go)
    user=> (reset)

## License

Copyright © 2016 Zalando SE

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   [http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
