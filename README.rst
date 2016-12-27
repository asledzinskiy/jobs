=====================
MCP CI Project config
=====================

Gerrit project namespaces structure:

  * <component-name> - namespace for component source code repositories
  * <component-name>-ci - namespaces for CI related stuff for component
    projects (pipeline jobs, docker files for slave images, tools and etc)

Example:

.. code-block:: text

    All-Projects
    ├── ccp/
    │   ├── fuel-ccp-heat/           # Docker and config templates to build Heat image
    │   ├── fuel-ccp-nova/           # Docker and config templates to build Nova image
    │   └── ...
    ├── ccp-ci/
    │   ├── ccp-build-pipeline/      # CCP pipeline jobs
    │   ├── ccp-run-tempest/         # Scripts to execute tempest
    │   ├── ccp-slave-image/         # Dockerfile to build CCP specific slaves
    │   └── ...
    ├── kubernetes/
    │   ├── kubernetes/              # Source code
    │   ├── ...
    └── kubernetes-ci/
        ├── kubernetes-slave-image/  # Dockerfile to build Kubernetes specific slaves
        └── ...
