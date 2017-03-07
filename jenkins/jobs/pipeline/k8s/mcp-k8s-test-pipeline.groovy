coverage = "n"
artifacts_dir = "_artifacts"
git_k8s_cache_dir = "/home/jenkins/kubernetes"
event = env.GERRIT_EVENT_TYPE ?: env.MANUAL_EVENT_TYPE
git_commit_tag_id = ''
timestamp = System.currentTimeMillis().toString()
kube_docker_registry = env.KUBE_DOCKER_REGISTRY
kube_docker_repo = 'hyperkube-amd64'
kube_namespace = 'mirantis/kubernetes'
conformance_docker_repo = 'k8s-conformance'
docker_dev_repo = "docker-dev-local"
docker_prod_repo = "docker-prod-local"
binary_dev_repo = "binary-dev-local"
binary_prod_repo = "binary-prod-local"
server = Artifactory.server('mcp-ci')
artifactoryUrl = server.getUrl()
artifactory_tools = new com.mirantis.mcp.MCPArtifactory()
git_tools = new com.mirantis.mcp.Git()
buildInfo = Artifactory.newBuildInfo()
buildDesc = ''
gitTools = new com.mirantis.mcp.Git()

if ( event == 'patchset-created' ) {
    build_publish_binaries()
    run_unit_tests()
    run_integration_tests()
    run_system_test()
} else if ( event == 'change-merged' ) {
    promote_artifacts()
} else if ( event == 'coverage-by-timer' ) {
    coverage = 'y'
    run_unit_tests()
} else if ( event == 'hyperkube-build-by-sha' ) {
    if ( ! env.GIT_SHA ) {
        error('You have to specify git sha for some commit')
    }
    git_sha = env.GIT_SHA
    build_publish_binaries()
}

def run_unit_tests () {
    stage('unit-tests') {
        node ('k8s') {
            def docker_image_unit = "${env.DOCKER_IMAGE_UNIT}"
            deleteDir()
            if ( env.GERRIT_EVENT_TYPE ) {
                gitTools.gerritPatchsetCheckout([
                    credentialsId : "mcp-ci-gerrit"
                ])
            } else {
                def downstream_branch = "${env.DOWNSTREAM_BRANCH}"
                def gerrit_host = "${env.GERRIT_HOST}"
                gitTools.gitSSHCheckout ([
                    credentialsId : "mcp-ci-gerrit",
                    branch : "${downstream_branch}",
                    host : "${gerrit_host}",
                    project : "kubernetes/kubernetes"
                ])
            }
            sh "mkdir ${env.WORKSPACE}/${artifacts_dir}"
            withEnv(["COVERAGE=${coverage}",
                     "DOCKER_IMAGE=${docker_image_unit}",
                     "WORKSPACE=${env.WORKSPACE}"]) {
                try {
                    sh '''
                       docker rmi ${DOCKER_IMAGE} || true
                       docker pull ${DOCKER_IMAGE}
                       docker run --rm=true -v ${WORKSPACE}:/workspace -e KUBE_COVER=${COVERAGE} ${DOCKER_IMAGE}
                    '''
                } catch (InterruptedException x) {
                    echo "The job was aborted"
                } finally {
                    archiveArtifacts allowEmptyArchive: true, artifacts: '_artifacts/*', excludes: null
                    junit keepLongStdio: true, testResults: '_artifacts/**.xml'
                    sh "sudo chown -R jenkins:jenkins ${env.WORKSPACE}"
                }
            }

        }
    }
}

def run_integration_tests () {
    def docker_image_int = "${env.DOCKER_IMAGE_INTEGRATION}"
    parallel integration: {
        stage('integration-tests') {
            node('k8s') {
                deleteDir()
                gitTools.gerritPatchsetCheckout ([
                    credentialsId : "mcp-ci-gerrit"
                ])
                sh "mkdir ${env.WORKSPACE}/${artifacts_dir}"
                try {
                    withEnv(["COVERAGE=${coverage}",
                             "DOCKER_IMAGE=${docker_image_int}",
                             "WORKSPACE=${env.WORKSPACE}"]) {
                        sh '''
                        docker rmi ${DOCKER_IMAGE} || true
                        docker pull ${DOCKER_IMAGE}
                        docker run --rm=true -v ${WORKSPACE}:/workspace -e KUBE_COVER=${COVERAGE} ${DOCKER_IMAGE}
                    '''
                    }
                } catch (InterruptedException x) {
                    echo "The job was aborted"
                } finally {
                    sh "sudo chown -R jenkins:jenkins ${env.WORKSPACE}"
                    archiveArtifacts allowEmptyArchive: true, artifacts: '_artifacts/*', excludes: null
                    junit keepLongStdio: true, testResults: '_artifacts/**.xml'
                }

            }
        }

    }, conformance: {
        stage('e2e-tests') {
            node('system-test') {

                def k8s_repo_dir = "${env.WORKSPACE}/kubernetes"
                def WORKSPACE = "${env.WORKSPACE}"
                def DEVOPS_DIR = WORKSPACE + "/utils/fuel-devops"
                def CONF_PATH = DEVOPS_DIR + "/default.yaml"
                def IMAGE_PATH = WORKSPACE + "/image.qcow2"
                def ENV_NAME = "mcp-k8s-e2e-conformance.${env.BUILD_NUMBER}"
                def VENV_DIR = "/home/jenkins/venv-fuel-devops-3.0"
                def DEVOPS_DB_ENGINE = "django.db.backends.sqlite3"
                def DEVOPS_DB_NAME = "/home/jenkins/venv-fuel-devops-3.0.sqlite3.db"
                def SSHPASS = "vagrant"

                deleteDir()
                def HOST = env.GERRIT_HOST
                gitTools.gitSSHCheckout ([
                  credentialsId : "mcp-ci-gerrit",
                  branch : "master",
                  host : HOST,
                  project : "mcp-ci/project-config"
                ])
                dir("${k8s_repo_dir}") {
                  gitTools.gerritPatchsetCheckout ([
                      credentialsId : "mcp-ci-gerrit"
                  ])
                }
                sh "mkdir ${env.WORKSPACE}/${artifacts_dir}"
                def downloadSpec = """{
                    "files": [
                    {
                      "pattern": "vm-images/packer/ubuntu-16.04*.qcow2",
                      "props": "com.mirantis.latest=true",
                      "target": "downloaded/"
                    }
                    ]
                }"""
                server.download(downloadSpec)
                sh "mv downloaded/packer/ubuntu-16.04*.qcow2 image.qcow2"
                withEnv(["CONF_PATH=${CONF_PATH}",
                         "IMAGE_PATH=${IMAGE_PATH}",
                         "ENV_NAME=${ENV_NAME}",
                         "DEVOPS_DIR=${DEVOPS_DIR}",
                         "VENV_DIR=${VENV_DIR}",
                         "DEVOPS_DB_ENGINE=${DEVOPS_DB_ENGINE}",
                         "DEVOPS_DB_NAME=${DEVOPS_DB_NAME}",
                         "SSHPASS=${SSHPASS}",
                         "artifacts_dir=${artifacts_dir}" ]) {
                  try {
                    writeFile file: WORKSPACE + '/create_env.sh', text: '''\
                      #!/bin/bash
                      source ${VENV_DIR}/bin/activate
                      python ${DEVOPS_DIR}/env_manage.py create_env && \
                      python ${DEVOPS_DIR}/env_manage.py get_node_ip > env_node_ip
                    '''.stripIndent()
                    writeFile file: WORKSPACE + '/erase_env.sh', text: '''\
                      #!/bin/bash
                      source ${VENV_DIR}/bin/activate
                      dos.py erase ${ENV_NAME}
                    '''.stripIndent()
                    sh '''
                      chmod +x ${WORKSPACE}/create_env.sh
                      chmod +x ${WORKSPACE}/erase_env.sh
                      ${WORKSPACE}/create_env.sh
                    '''
                    writeFile file: WORKSPACE + '/run_tests_on_node.sh', text: '''\
                      #!/bin/bash
                      set -ex
                      export GOPATH=/home/vagrant/_gopath
                      export PATH=${PATH}:/usr/local/go/bin:/usr/local/bin:${GOPATH}/bin
                      export ARTIFACTS=/home/vagrant/_artifacts
                      export KUBERNETES_PROVIDER=dind
                      export NUM_NODES=6
                      export KUBE_MASTER_IP=localhost
                      export KUBE_MASTER=localhost
                      export KUBERNETES_CONFORMANCE_TEST=y
                      export DOCKER_COMPOSE_URL=https://github.com/docker/compose/releases/download/1.8.0
                      mkdir -p ${ARTIFACTS}
                      sudo apt-get update
                      sudo apt-get -y install build-essential
                      wget https://storage.googleapis.com/golang/go1.7.4.linux-amd64.tar.gz
                      sudo tar -xzf go*.tar.gz -C /usr/local/
                      sudo chown -R vagrant.vagrant /usr/local/go
                      go get -u github.com/jteeuwen/go-bindata/go-bindata
                      cd kubernetes/
                      git clone https://github.com/sttts/kubernetes-dind-cluster.git dind
                      # newer kubernetes-dind-cluster is broken for stable kubernetes
                      (cd dind && git checkout 885a308d6334e91e28189e31e7fc72e5bed7063c)
                      curl -L ${DOCKER_COMPOSE_URL}/docker-compose-$(uname -s)-$(uname -m) | sudo tee /usr/local/bin/docker-compose >/dev/null
                      sudo chmod +x "/usr/local/bin/docker-compose"
                      make WHAT="cmd/hyperkube cmd/kubectl vendor/github.com/onsi/ginkgo/ginkgo test/e2e/e2e.test"
                      dind/dind-up-cluster.sh
                      GINKGO_PARALLEL=y \
                      go run hack/e2e.go --v --test -check_version_skew=false \
                           --test_args="--ginkgo.focus=\\[Conformance\\] --ginkgo.skip=\\[Serial\\] --ginkgo.noColor --report-dir=${ARTIFACTS} \
                           --host=https://localhost:6443"
                      go run hack/e2e.go --v --test -check_version_skew=false \
                           --test_args="--ginkgo.focus=\\[Serial\\].*\\[Conformance\\] --ginkgo.noColor --report-dir=${ARTIFACTS} \
                           --host=https://localhost:6443"
                    '''.stripIndent()
                    sh '''
                      sleep 1m
                      export ENV_NODE_IP=$(cat env_node_ip)
                      sshpass -e scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -r kubernetes vagrant@${ENV_NODE_IP}:.
                      sshpass -e scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no run_tests_on_node.sh vagrant@${ENV_NODE_IP}:.
                      sshpass -e ssh -t -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no vagrant@${ENV_NODE_IP} "bash ./run_tests_on_node.sh"
                      sshpass -e scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -rp vagrant@${ENV_NODE_IP}:~/${artifacts_dir} .
                    '''
                  } catch (InterruptedException x) {
                    echo "The job was aborted"
                  } finally {
                    dir("${env.WORKSPACE}") {
                      sh "./erase_env.sh"
                      junit keepLongStdio: true, testResults: artifacts_dir + '/**.xml'
                    }
                  }
                }
            }
        }
    },
    failFast: true
}

def build_publish_binaries () {
    def kube_docker_owner = 'jenkins'
    if (!kube_docker_registry) {
        error('KUBE_DOCKER_REGISTRY must be set')
    }
    parallel hyperkube_image: {
        stage('hyperkube-build') {
            node('k8s') {
                sh "sudo chown -R jenkins:jenkins ${env.WORKSPACE}"
                deleteDir()

                def k8s_base_image = env.K8S_BASE_IMAGE
                def calico_cni_image_repo = env.CALICO_CNI_IMAGE_REPO
                def calico_cni_image_tag = env.CALICO_CNI_IMAGE_TAG
                def k8s_repo_dir = "${env.WORKSPACE}/kubernetes"

                def registry = "${kube_docker_registry}/${kube_docker_owner}/${kube_namespace}"

                if (!k8s_base_image) {
                    error('K8S_BASE_IMAGE must be set')
                }

                if ("${env.CALICO_DOWNSTREAM}" == "true") {
                    calico_cni_image_repo = "${env.CALICO_DOCKER_REGISTRY}/mirantis/projectcalico/calico/cni"
                    calico_cni_image_tag = "latest"
                }

                writeFile file: 'build.sh', text: '''\
                    #!/bin/bash -xe
                    if [ -f ${WORKSPACE}/kubernetes/build/run.sh ]; then
                        ${WORKSPACE}/kubernetes/build/run.sh make WHAT=cmd/hyperkube
                    else
                        ${WORKSPACE}/kubernetes/build-tools/run.sh make WHAT=cmd/hyperkube
                    fi
                '''.stripIndent()

                dir("${k8s_repo_dir}") {
                    if ( env.GERRIT_EVENT_TYPE ) {
                        gitTools.gerritPatchsetCheckout([
                            credentialsId : "mcp-ci-gerrit"
                        ])
                        git_commit_tag_id = git_tools.getGitDescribe(true)
                    } else {
                        def gerrit_host = "${env.GERRIT_HOST}"
                        gitTools.gitSSHCheckout ([
                            credentialsId : "mcp-ci-gerrit",
                            branch : "master",
                            host : "${gerrit_host}",
                            project : "kubernetes/kubernetes"
                        ])
                        git_commit_tag_id = git_sha
                        sh "git checkout ${git_sha}"
                    }

                    def kube_docker_version = "${git_commit_tag_id}_${timestamp}"
                    def version = "${kube_docker_version}"
                    def kube_build_version = sh(script: 'bash -c \'KUBE_ROOT=$(pwd) && \
                                                         . build/common.sh || . build-tools/common.sh && kube::build::verify_prereqs >&2 && \
                                                         KUBE_DATA_CONTAINER_NAME=${KUBE_DATA_CONTAINER_NAME:-$KUBE_BUILD_DATA_CONTAINER_NAME} && \
                                                         echo $KUBE_DATA_CONTAINER_NAME\'',
                                                returnStdout: true).trim()
                    def kube_build_image_version = sh(script: 'bash -c \'KUBE_ROOT=$(pwd) && \
                                                               . build/common.sh || . build-tools/common.sh && kube::build::verify_prereqs >&2 && \
                                                               echo ${KUBE_BUILD_IMAGE}\'',
                                                returnStdout: true).trim()

                    sh "docker rm -f ${kube_build_version} || true"
                    sh "docker rmi -f ${kube_build_image_version} || true"

                    withEnv(["VERSION=${version}",
                             "KUBE_DOCKER_VERSION=${kube_docker_version}",
                             "REGISTRY=${registry}",
                             "BASEIMAGE=${k8s_base_image}",
                             "CALICO_CNI_IMAGE_REPO=${calico_cni_image_repo}",
                             "CALICO_CNI_IMAGE_TAG=${calico_cni_image_tag}",
                             "GERRIT_PATCHSET_REVISION=${env.GERRIT_PATCHSET_REVISION}",
                             "GERRIT_CHANGE_URL=${env.GERRIT_CHANGE_URL}",
                             "BUILD_URL=${env.BUILD_URL}",
                             "KUBE_DOCKER_REPOSITORY=${kube_namespace}/${kube_docker_repo}",
                             "KUBE_DOCKER_OWNER=${kube_docker_owner}",
                             "KUBE_BUILD_VERSION=${kube_build_version}",
                             "KUBE_BUILD_IMAGE_VERSION=${kube_build_image_version}",
                             "ARTIFACTORY_USER_EMAIL=jenkins@mcp-ci-artifactory",
                             //"KUBE_DOCKER_REGISTRY=${kube_docker_registry}",
                             "KUBE_CONTAINER_TMP=hyperkube-tmp-${env.BUILD_NUMBER}",
                             "CALICO_BINDIR=/opt/cni/bin",
                             // downstream options
                             "CALICO_DOWNSTREAM=${env.CALICO_DOWNSTREAM}",
                             "ARTIFACTORY_URL=${artifactoryUrl}"]) {

                        try {
                            sh '''
                            chmod +x ${WORKSPACE}/build.sh
                            sudo -E -s ${WORKSPACE}/build.sh
                            '''

                            sh "make -C cluster/images/hyperkube build"
                            echo "Calico injection will happen now..."
                            sh '''
                                docker run --rm -v $(pwd):/cnibindir ${CALICO_CNI_IMAGE_REPO}:${CALICO_CNI_IMAGE_TAG} sh -c 'cp -a /opt/cni/bin/* /cnibindir/'
                                sudo chown jenkins:jenkins calico calico-ipam
                                chmod +x calico calico-ipam
                            '''

                            sh '''
                                cat <<EOF > Dockerfile.build
                                FROM ${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_OWNER}/${KUBE_DOCKER_REPOSITORY}:${KUBE_DOCKER_VERSION}
                                # Apply additional build metadata
                                LABEL com.mirantis.image-specs.gerrit_change_url="${GERRIT_CHANGE_URL}" \
                                com.mirantis.image-specs.changeid="${GERRIT_CHANGE_ID}" \
                                com.mirantis.image-specs.version="${KUBE_DOCKER_VERSION}"
                            '''
                            sh '''
                                mkdir -p ${WORKSPACE}/kubernetes/artifacts
                                docker build -t ${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_OWNER}/${KUBE_DOCKER_REPOSITORY}:${KUBE_DOCKER_VERSION} - < Dockerfile.build
                                docker run --name "${KUBE_CONTAINER_TMP}" -d -t "${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_OWNER}/${KUBE_DOCKER_REPOSITORY}:${KUBE_DOCKER_VERSION}"
                                docker exec -t "${KUBE_CONTAINER_TMP}" /bin/bash -c "/bin/mkdir -p ${CALICO_BINDIR}"
                                docker cp calico "${KUBE_CONTAINER_TMP}":"${CALICO_BINDIR}/calico"
                                docker cp calico-ipam "${KUBE_CONTAINER_TMP}":"${CALICO_BINDIR}/calico-ipam"
                                docker cp "${KUBE_CONTAINER_TMP}":/hyperkube "${WORKSPACE}/kubernetes/artifacts/hyperkube_${VERSION}"
                                docker stop "${KUBE_CONTAINER_TMP}"
                                docker commit "${KUBE_CONTAINER_TMP}" "${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_OWNER}/${KUBE_DOCKER_REPOSITORY}:${KUBE_DOCKER_VERSION}"
                                docker rm "${KUBE_CONTAINER_TMP}"
                                docker tag "${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_OWNER}/${KUBE_DOCKER_REPOSITORY}:${KUBE_DOCKER_VERSION}" "${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_REPOSITORY}:${KUBE_DOCKER_VERSION}"
                            '''

                            stage('hyperkube-publish') {
                                def uploadSpec = """{
                                  "files": [
                                    {
                                      "pattern": "artifacts/hyperkube**",
                                       "target": "${binary_dev_repo}/${kube_namespace}/hyperkube-binaries/"
                                    }
                                 ]
                                }"""
                                artifactory_tools.uploadBinariesToArtifactory(server, buildInfo, uploadSpec, true)
                                artifactory_tools.uploadImageToArtifactory(server, "${kube_docker_registry}",
                                                               "${kube_namespace}/${kube_docker_repo}",
                                                               "${kube_docker_version}", "${docker_dev_repo}")
                                buildDesc = "<b>hyperkube-image:</b> ${kube_docker_registry}/${kube_namespace}/${kube_docker_repo}:${kube_docker_version}<br>\
                                             <b>hyperkube-binary:</b> ${artifactoryUrl}/${binary_dev_repo}/${kube_namespace}/hyperkube-binaries/hyperkube_${kube_docker_version}<br>"
                                currentBuild.description = buildDesc
                            }
                        } catch (InterruptedException x) {
                            echo "The job was aborted"
                        } finally {
                            sh '''
                                docker rmi -f "${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_REPOSITORY}:${KUBE_DOCKER_VERSION}" || true
                                docker rmi -f "${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_OWNER}/${KUBE_DOCKER_REPOSITORY}:${KUBE_DOCKER_VERSION}" || true
                                docker rm -f "${KUBE_BUILD_VERSION}" || true
                                docker rmi -f "${KUBE_BUILD_IMAGE_VERSION}" || true
                                rm -f Dockerfile.build
                            '''
                            sh "sudo chown -R jenkins:jenkins ${env.WORKSPACE}"
                        }
                    }
                }
            }
        }
    }, conformance_image: {
        stage('conformance-build') {
            node('k8s') {
                sh "sudo chown -R jenkins:jenkins ${env.WORKSPACE}"
                deleteDir()

                def kube_docker_conformance_repository = 'k8s-conformance'
                def registry = "${kube_docker_registry}/${kube_docker_owner}/${kube_namespace}"
                def workspace = "${env.WORKSPACE}"

                def gerrit_host = "${env.GERRIT_HOST}"
                if ( env.GERRIT_EVENT_TYPE ) {
                    gitTools.gerritPatchsetCheckout([
                        credentialsId : "mcp-ci-gerrit"
                    ])
                    git_commit_tag_id = git_tools.getGitDescribe(true)
                } else {
                    gitTools.gitSSHCheckout ([
                        credentialsId : "mcp-ci-gerrit",
                        branch : "master",
                        host : "${gerrit_host}",
                        project : "kubernetes/kubernetes"
                    ])
                    git_commit_tag_id = git_sha
                    sh "git checkout ${git_sha}"
                }

                def projectRepoDir = "/tmp/project-config-${timestamp}"
                def helpersDir = "${projectRepoDir}/jenkins/jobs/builders/groovy-builders"
                gitTools.gitSSHCheckout ([
                    credentialsId : "mcp-ci-gerrit",
                    branch : "master",
                    host : "${gerrit_host}",
                    project : "mcp-ci/project-config",
                    targetDir : projectRepoDir
                ])

                def kube_docker_version = "${git_commit_tag_id}_${timestamp}"
                def kube_build_version = sh(script: 'bash -c \'KUBE_ROOT=$(pwd) && \
                                                     . build/common.sh || . build-tools/common.sh && kube::build::verify_prereqs >&2 && \
                                                     KUBE_DATA_CONTAINER_NAME=${KUBE_DATA_CONTAINER_NAME:-$KUBE_BUILD_DATA_CONTAINER_NAME} && \
                                                     echo $KUBE_DATA_CONTAINER_NAME\'',
                        returnStdout: true).trim()
                def kube_build_image_version = sh(script: 'bash -c \'KUBE_ROOT=$(pwd) && \
                                                           . build/common.sh || . build-tools/common.sh && kube::build::verify_prereqs >&2 && \
                                                           echo ${KUBE_BUILD_IMAGE}\'',
                        returnStdout: true).trim()

                sh "docker rm -f ${kube_build_version} || true"
                sh "docker rmi -f ${kube_build_image_version} || true"


                withEnv(["WORKSPACE=${env.WORKSPACE}",
                         "KUBE_DOCKER_VERSION=${kube_docker_version}",
                         "REGISTRY=${registry}",
                         "KUBE_DOCKER_REPOSITORY=${kube_namespace}/${kube_docker_repo}",
                         "KUBE_DOCKER_OWNER=${kube_docker_owner}",
                         "KUBE_BUILD_VERSION=${kube_build_version}",
                         "KUBE_BUILD_IMAGE_VERSION=${kube_build_image_version}",
                         "ARTIFACTORY_USER_EMAIL=jenkins@mcp-ci-artifactory",
                         //"KUBE_DOCKER_REGISTRY=${registry}",
                         "KUBE_DOCKER_CONFORMANCE_TAG=${kube_docker_registry}/${kube_namespace}/${kube_docker_conformance_repository}:${kube_docker_version}",
                         "ARTIFACTORY_URL=${artifactoryUrl}",
                         "GERRIT_PATCHSET_REVISION=${env.GERRIT_PATCHSET_REVISION}",
                         "GERRIT_CHANGE_URL=${env.GERRIT_CHANGE_URL}",
                         "BUILD_URL=${env.BUILD_URL}",
                         "KUBERNETES_PROVIDER=skeleton",
                         "KUBERNETES_CONFORMANCE_TEST=y"]) {
                    try {
                        sh '''
                            make -C "${WORKSPACE}" release-skip-tests
                            mkdir "${WORKSPACE}/_build_test_runner"
                            mv "${WORKSPACE}/_output/release-tars/kubernetes-test.tar.gz" \
                               "${WORKSPACE}/_output/release-tars/kubernetes.tar.gz" \
                               "${WORKSPACE}/_output/release-tars/kubernetes-client-linux-amd64.tar.gz" \
                               "${WORKSPACE}/_build_test_runner"
                        '''
                        writeFile file: workspace + '/_build_test_runner/Dockerfile', text: """\
                            FROM golang:1.7.4

                            RUN mkdir -p /go/src/k8s.io/kubernetes/_output/bin
                            ADD kubernetes-test.tar.gz /go/src/k8s.io/
                            ADD kubernetes.tar.gz /go/src/k8s.io/
                            ADD kubernetes-client-linux-amd64.tar.gz /go/src/k8s.io/
                            COPY entrypoint.sh /
                            RUN chmod +x /entrypoint.sh && \
                                mv /go/src/k8s.io/kubernetes/client/bin/kubectl /go/src/k8s.io/kubernetes/_output/bin/kubectl
                            WORKDIR /go/src/k8s.io/kubernetes
                            CMD /entrypoint.sh
                            LABEL com.mirantis.image-specs.gerrit_change_url="${env.GERRIT_CHANGE_URL}" \
                            com.mirantis.image-specs.build_url="${env.BUILD_URL}" \
                            com.mirantis.image-specs.patchset="${env.GERRIT_PATCHSET_REVISION}"
                        """.stripIndent()

                        sh "cp ${helpersDir}/conformance-entrypoint.sh ${workspace}/_build_test_runner/entrypoint.sh"
                        sh 'docker build -t "${KUBE_DOCKER_CONFORMANCE_TAG}" "${WORKSPACE}/_build_test_runner"'
                        stage('conformance-publish') {
                            artifactory_tools.uploadImageToArtifactory(server, "${kube_docker_registry}",
                                                           "${kube_namespace}/${conformance_docker_repo}",
                                                           "${kube_docker_version}", "${docker_dev_repo}")
                        }
                    } catch (InterruptedException x) {
                        echo "The job was aborted"
                    } finally {
                        sh '''
                           docker rmi -f ${KUBE_DOCKER_CONFORMANCE_TAG} || true
                           docker rm -f "${KUBE_BUILD_VERSION}" || true
                           docker rmi -f "${KUBE_BUILD_IMAGE_VERSION}" || true
                        '''
                        sh "sudo chown -R jenkins:jenkins ${env.WORKSPACE}"
                    }
                    buildDesc = "${buildDesc}conformance-image: ${kube_docker_registry}/${kube_namespace}/${conformance_docker_repo}:${kube_docker_version}"
                    currentBuild.description = buildDesc
                    archiveArtifacts allowEmptyArchive: true, artifacts: '_artifacts', excludes: null
                }
            }
        }
    },
    failFast: true

}

def run_system_test () {
    stage('system-tests') {
        build job: 'calico.system-test.deploy',
                parameters: [
                        string(name: 'HYPERKUBE_IMAGE_TAG', value: "${git_commit_tag_id}_${timestamp}"),
                        string(name: 'HYPERKUBE_IMAGE_REPO', value: "${kube_docker_registry}/${kube_namespace}/${kube_docker_repo}"),
                        string(name: 'GERRIT_CHANGE_URL', value: "${env.GERRIT_CHANGE_URL}"),
                        string(name: 'MCP_BRANCH', value: "${env.CALICO_VER}")]
    }
}

def promote_artifacts () {
    stage('promote') {
        node('k8s') {
            def properties = ['com.mirantis.gerritChangeId': "${env.GERRIT_CHANGE_ID}",
                              'com.mirantis.gerritPatchsetNumber': "${env.GERRIT_PATCHSET_NUMBER}",
                              'com.mirantis.targetImg': "${kube_namespace}/${kube_docker_repo}" ]
            // Search for an artifact with required properties
            def artifact_uri = artifactory_tools.uriByProperties(server.getUrl(), properties)
            // Get build info: build id and job name
            if ( artifact_uri ) {
                def buildInfo = artifactory_tools.getPropertiesForArtifact(artifact_uri)
                def currentTag = buildInfo.get('com.mirantis.targetTag').join(',')
                def targetTag = currentTag.split("_")[0]
                def promotionConfig = [
                        'buildName'  : buildInfo.get('com.mirantis.buildName').join(','), // value for each key property is an array
                        'buildNumber': buildInfo.get('com.mirantis.buildNumber').join(','),
                        'targetRepo' : binary_prod_repo.toString()]
                // promote build artifacts except docker because of jfrog bug
                server.promote promotionConfig
                // promote docker image
                artifactory_tools.promoteDockerArtifact(server.getUrl(),
                                            docker_dev_repo,
                                            docker_prod_repo,
                                            "${kube_namespace}/${kube_docker_repo}",
                                            currentTag,
                                            targetTag,
                                            true)
                artifactory_tools.promoteDockerArtifact(server.getUrl(),
                                            docker_dev_repo,
                                            docker_prod_repo,
                                            "${kube_namespace}/${kube_docker_repo}",
                                            currentTag,
                                            'latest')
                buildDesc = "<b>hyperkube-image:</b> ${kube_docker_registry}/${kube_namespace}/${kube_docker_repo}:${targetTag}<br>\
                             <b>hyperkube-binary:</b> ${artifactoryUrl}/${binary_prod_repo}/${kube_namespace}/hyperkube-binaries/hyperkube_${currentTag}<br>"
                currentBuild.description = buildDesc
            } else {
                echo 'Artifacts were not found, nothing to promote'
            }
        }
    }
}
