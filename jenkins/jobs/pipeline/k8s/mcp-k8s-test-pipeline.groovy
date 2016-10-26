coverage = "n"
artifacts_dir = "_artifacts"
git_k8s_cache_dir = "/home/jenkins/kubernetes"
event = env.GERRIT_EVENT_TYPE
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
buildInfo = Artifactory.newBuildInfo()

if ( event == 'patchset-created' ) {
    run_unit_tests()
    run_integration_tests()
    build_publish_binaries()
    run_system_test()
} else if ( event == 'change-merged' ) {
    promote_artifacts()
}

def run_unit_tests () {
    stage('unit-tests') {
        node ('k8s') {
            def docker_image_unit = "${env.DOCKER_IMAGE_UNIT}"
            deleteDir()
            clone_k8s_repo()
            gerritPatchsetCheckout{
                credentialsId = "mcp-ci-gerrit"
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
                clone_k8s_repo()
                gerritPatchsetCheckout {
                    credentialsId = "mcp-ci-gerrit"
                }
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
            node('k8s-e2e') {

                def k8s_repo_dir = "${env.WORKSPACE}/kubernetes"

                deleteDir()
                sh "mkdir ${env.WORKSPACE}/${artifacts_dir}"
                dir("${k8s_repo_dir}") {
                    clone_k8s_repo()
                    gerritPatchsetCheckout {
                        credentialsId = "mcp-ci-gerrit"
                    }
                    withEnv(["PATH=${env.PATH}:/usr/local/go/bin",
                             "ARTIFACTS=${env.WORKSPACE}/${artifacts_dir}",
                             "GOPATH=${env.WORKSPACE}/_gopath",
                             "KUBERNETES_VAGRANT_USE_NFS=true",
                             "KUBERNETES_PROVIDER=vagrant",
                             "NUM_NODES=2",
                             "KUBERNETES_MEMORY=3072",
                             "REPORT_DIR=${env.WORKSPACE}/${artifacts_dir}"]) {
                        try {
                            sh '''
                           make clean
                           ./cluster/kube-down.sh || true
                           make release-skip-tests
                           ./cluster/kube-up.sh
                           go run hack/e2e.go -v --test --test_args="--report-dir=${REPORT_DIR} --ginkgo.focus=\\[Conformance\\]"
                        '''
                        } catch (InterruptedException x) {
                            echo "The job was aborted"
                        } finally {
                            sh './cluster/kube-down.sh'
                            sh "sudo chown -R jenkins:jenkins ${env.WORKSPACE}"
                            dir("${env.WORKSPACE}") {
                                junit keepLongStdio: true, testResults: '_artifacts/**.xml'
                            }
                        }
                    }
                }
            }
        }
    },
    failFast: true
}

def build_publish_binaries () {
    parallel hyperkube_image: {
        stage('hyperkube-build') {
            node('k8s') {
                deleteDir()

                def calico_cni = env.CALICO_CNI
                def calico_ipam = env.CALICO_IPAM
                def k8s_repo_dir = "${env.WORKSPACE}/kubernetes"

                def kube_docker_owner = 'jenkins'
                def registry = "${kube_docker_registry}/${kube_docker_owner}/${kube_namespace}"

                if (!kube_docker_registry) {
                    error('KUBE_DOCKER_REGISTRY must be set')
                }
                if (!kube_docker_owner) {
                    error('KUBE_DOCKER_OWNER must be set')
                }
                if (!calico_cni) {
                    calico_cni = "https://github.com/projectcalico/calico-cni/releases/download/v1.3.1/calico"
                }
                if (!calico_ipam) {
                    calico_ipam = "https://github.com/projectcalico/calico-cni/releases/download/v1.3.1/calico-ipam"
                }

                dir("${k8s_repo_dir}") {
                    clone_k8s_repo()
                    gerritPatchsetCheckout {
                        credentialsId = "mcp-ci-gerrit"
                    }
                    git_commit_tag_id = generate_git_version()

                    def kube_docker_version = "${git_commit_tag_id}_${timestamp}"
                    def version = "${kube_docker_version}"

                    withEnv(["WORKSPACE=${env.WORKSPACE}/kubernetes",
                             "VERSION=${version}",
                             "KUBE_DOCKER_VERSION=${kube_docker_version}",
                             "REGISTRY=${registry}",
                             "CALICO_CNI=${calico_cni}",
                             "CALICO_IPAM=${calico_ipam}",
                             "GERRIT_PATCHSET_REVISION=${env.GERRIT_PATCHSET_REVISION}",
                             "GERRIT_CHANGE_URL=${env.GERRIT_CHANGE_URL}",
                             "BUILD_URL=${env.BUILD_URL}",
                             "KUBE_DOCKER_REPOSITORY=${kube_namespace}/${kube_docker_repo}",
                             "KUBE_DOCKER_OWNER=${kube_docker_owner}",
                             "ARTIFACTORY_USER_EMAIL=jenkins@mcp-ci-artifactory",
                             //"KUBE_DOCKER_REGISTRY=${kube_docker_registry}",
                             "KUBE_CONTAINER_TMP=hyperkube-tmp-${env.BUILD_NUMBER}",
                             "CALICO_BINDIR=/opt/cni/bin",
                             // downstream options
                             "CALICO_DOWNSTREAM=${env.CALICO_DOWNSTREAM}",
                             "ARTIFACTORY_URL=${env.ARTIFACTORY_URL}",
                             "CALICO_VER=${env.CALICO_VER}"]) {
                        writeFile file: 'build.sh', text: '''#!/bin/bash
                        source "${WORKSPACE}/build/common.sh"
                        kube::build::verify_prereqs
                        kube::build::build_image
                        kube::build::run_build_command hack/build-go.sh cmd/hyperkube
                    '''.stripIndent()
                        try {
                            sh '''
                            chmod +x build.sh
                            sudo -E -s ${WORKSPACE}/build.sh
                        '''
                            dir("cluster/images/hyperkube") {
                                sh '''
                                if grep -q 'LABEL com.mirantis' Dockerfile; then
                                    sed -i.back '/.*com.mirantis.*/d' Dockerfile
                                fi
                                cat <<EOF>> Dockerfile
                                # Apply additional build metadata
                                LABEL com.mirantis.image-specs.gerrit_change_url="${GERRIT_CHANGE_URL}" \
                                  com.mirantis.image-specs.changeid="${GERRIT_CHANGE_ID}" \
                                  com.mirantis.image-specs.version="${KUBE_DOCKER_VERSION}"
                            '''
                            }
                            sh "make -C cluster/images/hyperkube build"
                            echo "Calico injection will happen now..."
                            if ("${env.CALICO_DOWNSTREAM}" == "true") {
                                sh '''#!/bin/bash
                                TMPURL=${ARTIFACTORY_URL}/projectcalico/${CALICO_VER}/calico-cni
                                lastbuild=\$(curl -s $TMPURL/lastbuild)
                                wget ${TMPURL}/calico-${lastbuild} -O calico
                                wget ${TMPURL}/calico-ipam-${lastbuild} -O calico-ipam
                                calico_checksum=\$(sha1sum calico | awk '{ print $1 }')
                                calico_ipam_checksum=\$(sha1sum calico-ipam | awk '{ print $1 }')
                                [ "$calico_checksum" == "\$(curl -s ${TMPURL}/calico-${lastbuild}.sha1)" ]
                                [ "$calico_ipam_checksum" == "\$(curl -s ${TMPURL}/calico-ipam-${lastbuild}.sha1)" ]
                            '''
                            } else {
                                sh '''
                                wget "${CALICO_IPAM}" -O calico-ipam
                                wget "${CALICO_CNI}" -O calico
                            '''
                            }

                            sh '''
                            chmod +x calico calico-ipam
                            mkdir -p ${WORKSPACE}/artifacts
                            docker run --name "${KUBE_CONTAINER_TMP}" -d -t "${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_OWNER}/${KUBE_DOCKER_REPOSITORY}:${KUBE_DOCKER_VERSION}"
                            docker exec -t "${KUBE_CONTAINER_TMP}" /bin/bash -c "/bin/mkdir -p ${CALICO_BINDIR}"
                            docker cp calico "${KUBE_CONTAINER_TMP}":"${CALICO_BINDIR}/calico"
                            docker cp calico-ipam "${KUBE_CONTAINER_TMP}":"${CALICO_BINDIR}/calico-ipam"
                            docker cp "${KUBE_CONTAINER_TMP}":/hyperkube "${WORKSPACE}/artifacts/hyperkube_${VERSION}"
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
                                upload_binaries_to_artifactory(uploadSpec, true)
                                upload_image_to_artifactory("${kube_docker_registry}", "${kube_namespace}/${kube_docker_repo}", "${kube_docker_version}", "${docker_dev_repo}")
                                currentBuild.description = "${kube_docker_registry}/${kube_namespace}/${kube_docker_repo}:${kube_docker_version}"
                            }
                        } catch (InterruptedException x) {
                            echo "The job was aborted"
                        } finally {
                            sh '''
                            docker rmi -f "${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_REPOSITORY}:${KUBE_DOCKER_VERSION}" || true
                            docker rmi -f "${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_OWNER}/${KUBE_DOCKER_REPOSITORY}:${KUBE_DOCKER_VERSION}" || true
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
                deleteDir()

                def kube_docker_conformance_repository = 'k8s-conformance'
                def kube_docker_owner = 'jenkins'
                def registry = "${kube_docker_registry}/${kube_docker_owner}/${kube_namespace}"
                def workspace = "${env.WORKSPACE}"

                if (!kube_docker_registry) {
                    error('KUBE_DOCKER_REGISTRY must be set')
                }
                if (!kube_docker_owner) {
                    error('KUBE_DOCKER_OWNER must be set')
                }
                if (!kube_docker_conformance_repository) {
                    error('KUBE_DOCKER_CONFORMANCE_REPOSITORY must be set')
                }

                clone_k8s_repo()
                gerritPatchsetCheckout {
                    credentialsId = "mcp-ci-gerrit"
                }
                def git_commit_tag_id = generate_git_version()
                def kube_docker_version = "${git_commit_tag_id}_${timestamp}"

                withEnv(["WORKSPACE=${env.WORKSPACE}",
                         "KUBE_DOCKER_VERSION=${kube_docker_version}",
                         "REGISTRY=${registry}",
                         "KUBE_DOCKER_REPOSITORY=${kube_namespace}/${kube_docker_repo}",
                         "KUBE_DOCKER_OWNER=${kube_docker_owner}",
                         "ARTIFACTORY_USER_EMAIL=jenkins@mcp-ci-artifactory",
                         //"KUBE_DOCKER_REGISTRY=${registry}",
                         "KUBE_DOCKER_CONFORMANCE_TAG=${kube_docker_registry}/${kube_namespace}/${kube_docker_conformance_repository}:${kube_docker_version}",
                         "ARTIFACTORY_URL=${env.ARTIFACTORY_URL}",
                         "KUBERNETES_PROVIDER=skeleton",
                         "KUBERNETES_CONFORMANCE_TEST=y",
                         "CALICO_VER=${env.CALICO_VER}"]) {
                    try {
                        sh """
                        make -C "${WORKSPACE}" release-skip-tests
                        mkdir "${WORKSPACE}/_build_test_runner"
                        mv "${WORKSPACE}/_output/release-tars/kubernetes-test.tar.gz" \
                           "${WORKSPACE}/_output/release-tars/kubernetes.tar.gz" \
                           "${WORKSPACE}/_build_test_runner"
                    """
                        writeFile file: workspace + '/_build_test_runner/Dockerfile', text: '''\
                      FROM golang:1.6.3

                      RUN mkdir -p /go/src/k8s.io
                      ADD kubernetes-test.tar.gz /go/src/k8s.io/
                      ADD kubernetes.tar.gz /go/src/k8s.io/
                      COPY entrypoint.sh /
                      RUN chmod +x /entrypoint.sh
                      WORKDIR /go/src/k8s.io/kubernetes
                      CMD /entrypoint.sh
                      LABEL com.mirantis.image-specs.gerrit_change_url="${GERRIT_CHANGE_URL}" \
                            com.mirantis.image-specs.build_url="${BUILD_URL}" \
                            com.mirantis.image-specs.patchset="${GERRIT_PATCHSET_REVISION}"
                    '''.stripIndent()

                        writeFile file: workspace + '/_build_test_runner/entrypoint.sh', text: '''\
                      #!/bin/bash
                      set -u -e

                      function escape_test_name() {
                          sed 's/[]\$*.^|()[]/\\&/g; s/\\s\\+/\\s+/g' <<< "\$1" | tr -d '\n'
                      }

                      TESTS_TO_SKIP=(
                          '[k8s.io] Port forwarding [k8s.io] With a server that expects no client request should support a client that connects, sends no data, and disconnects [Conformance]'
                          '[k8s.io] Port forwarding [k8s.io] With a server that expects a client request should support a client that connects, sends no data, and disconnects [Conformance]'
                          '[k8s.io] Port forwarding [k8s.io] With a server that expects a client request should support a client that connects, sends data, and disconnects [Conformance]'
                          '[k8s.io] Downward API volume should update annotations on modification [Conformance]'
                          '[k8s.io] DNS should provide DNS for services [Conformance]'
                          '[k8s.io] Kubectl client [k8s.io] Kubectl patch should add annotations for pods in rc [Conformance]'
                      )

                      function skipped_test_names () {
                          local first=y
                          for name in "${TESTS_TO_SKIP[@]}"; do
                              if [ -z "$first" ]; then
                                  echo -n "|"
                              else
                                  first=
                              fi
                              echo -n "$(escape_test_name "$name")\$"
                          done
                      }

                      FOCUS="${FOCUS:-}"
                      API_SERVER="${API_SERVER:-}"
                      if [ -z "$API_SERVER" ]; then
                          echo "Must provide API_SERVER env var" 1>&2
                          exit 1
                      fi

                      # Configure kube config
                      cluster/kubectl.sh config set-cluster local --server="$API_SERVER" --insecure-skip-tls-verify=true
                      cluster/kubectl.sh config set-context local --cluster=local --user=local
                      cluster/kubectl.sh config use-context local

                      if [ -z "$FOCUS" ]; then
                          # non-serial tests can be run in parallel mode
                          GINKGO_PARALLEL=y go run hack/e2e.go --v --test -check_version_skew=false \
                            --check_node_count=false \
                            --test_args="--ginkgo.focus=\\[Conformance\\] --ginkgo.skip=\\[Serial\\]|\\[Flaky\\]|\\[Feature:.+\\]|$(skipped_test_names)"

                          # serial tests must be run without GINKGO_PARALLEL
                          go run hack/e2e.go --v --test -check_version_skew=false --check_node_count=false \
                            --test_args="--ginkgo.focus=\\[Serial\\].*\\[Conformance\\] --ginkgo.skip=$(skipped_test_names)"
                      else
                          go run hack/e2e.go --v --test -check_version_skew=false --check_node_count=false \
                            --test_args="--ginkgo.focus=$(escape_test_name "$FOCUS")"
                      fi
                    '''.stripIndent()
                        sh 'docker build -t "${KUBE_DOCKER_CONFORMANCE_TAG}" "${WORKSPACE}/_build_test_runner"'
                        stage('conformance-publish') {
                            upload_image_to_artifactory("${kube_docker_registry}", "${kube_namespace}/${conformance_docker_repo}", "${kube_docker_version}", "${docker_dev_repo}")
                        }
                    } catch (InterruptedException x) {
                        echo "The job was aborted"
                    } finally {
                        sh "docker rmi -f ${KUBE_DOCKER_CONFORMANCE_TAG} || true"
                        sh "sudo chown -R jenkins:jenkins ${env.WORKSPACE}"
                    }
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
            def server = Artifactory.server('mcp-ci')
            def properties = ['com.mirantis.changeid': "${env.GERRIT_CHANGE_ID}",
                              'com.mirantis.patchset_number': "${env.GERRIT_PATCHSET_NUMBER}" ]
            // Search for an artifact with required properties
            def artifact_uri = uri_by_properties(env.ARTIFACTORY_URL, properties)
            // Get build info: build id and job name
            if ( artifact_uri ) {
                def buildInfo = get_properties_for_artifact(artifact_uri)
                def promotionConfig = [
                        'buildName'  : buildInfo.get('com.mirantis.build_name').join(','), // value for each key property is an array
                        'buildNumber': buildInfo.get('com.mirantis.build_id').join(','),
                        'targetRepo' : binary_prod_repo.toString()]
                // promote build artifacts except docker because of jfrog bug
                server.promote promotionConfig
                // promote docker image
                promote_docker_artifact(env.ARTIFACTORY_URL,
                        docker_dev_repo,
                        docker_prod_repo,
                        "${kube_namespace}/${kube_docker_repo}",
                        buildInfo.get('com.mirantis.target_tag').join(','),
                        buildInfo.get('com.mirantis.target_tag').join(',').split("_")[0],
                        true)
                promote_docker_artifact(env.ARTIFACTORY_URL,
                        docker_dev_repo,
                        docker_prod_repo,
                        "${kube_namespace}/${kube_docker_repo}",
                        buildInfo.get('com.mirantis.target_tag').join(','),
                        'latest')
            } else {
                echo 'Artifacts were not found, nothing to promote'
            }
        }
    }
}

def set_properties (artifact_url, properties, recursive=false) {
    def properties_str = 'properties='
    def key,value
    if (recursive) {
        recursive = 'recursive=1'
    } else {
        recursive = 'recursive=0'
    }
    for ( int i = 0; i < properties.size(); i++ ) {
        // avoid serialization errors
        key = properties.entrySet().toArray()[i].key
        value = properties.entrySet().toArray()[i].value
        properties_str += "${key}=${value}|"
    }
    def url = "${artifact_url}?${properties_str}&${recursive}"
    withCredentials([
            [$class: 'UsernamePasswordMultiBinding',
             credentialsId: 'artifactory',
             passwordVariable: 'ARTIFACTORY_PASSWORD',
             usernameVariable: 'ARTIFACTORY_LOGIN']
    ]) {
        sh "bash -c \"curl -X PUT -u ${ARTIFACTORY_LOGIN}:${ARTIFACTORY_PASSWORD} \'${url}\'\""
    }
}

def get_properties_for_artifact(artifact_url) {
    def url = "${artifact_url}?properties"
    def result
    withCredentials([
            [$class: 'UsernamePasswordMultiBinding',
             credentialsId: 'artifactory',
             passwordVariable: 'ARTIFACTORY_PASSWORD',
             usernameVariable: 'ARTIFACTORY_LOGIN']
    ]) {
        result = sh(script: "bash -c \"curl -X GET -u ${ARTIFACTORY_LOGIN}:${ARTIFACTORY_PASSWORD} \'${url}\'\"",
                returnStdout: true).trim()
    }
    def properties = new groovy.json.JsonSlurperClassic().parseText(result)
    return properties.get("properties")
}

def promote_docker_artifact(artifactory_url, docker_dev_repo, docker_prod_repo,
                            docker_repo, artifact_tag, target_tag, copy=false) {
    def url = "${artifactory_url}/api/docker/${docker_dev_repo}/v2/promote"
    withCredentials([
            [$class: 'UsernamePasswordMultiBinding',
             credentialsId: 'artifactory',
             passwordVariable: 'ARTIFACTORY_PASSWORD',
             usernameVariable: 'ARTIFACTORY_LOGIN']
    ]) {
        writeFile file: "query.json",
                text: """{
                  \"targetRepo\": \"${docker_prod_repo}\",
                  \"dockerRepository\": \"${docker_repo}\",
                  \"tag\": \"${artifact_tag}\",
                  \"targetTag\" : \"${target_tag}\",
                  \"copy\": \"${copy}\"
              }""".stripIndent()
        sh 'cat query.json'
        sh "bash -c \"curl  -u ${ARTIFACTORY_LOGIN}:${ARTIFACTORY_PASSWORD} -H \"Content-Type:application/json\" -X POST -d @query.json ${url}\""
    }
}

def uri_by_properties(artifactory_url, properties) {
    def key, value
    def properties_str = ''
    for ( int i = 0; i < properties.size(); i++ ) {
        // avoid serialization errors
        key = properties.entrySet().toArray()[i].key
        value = properties.entrySet().toArray()[i].value
        properties_str += "${key}=${value}&"
    }
    def search_url = "${artifactory_url}/api/search/prop?${properties_str}"

    def result = sh(script: "bash -c \"curl -X GET \'${search_url}\'\"",
            returnStdout: true).trim()
    def content = new groovy.json.JsonSlurperClassic().parseText(result)
    def uri = content.get("results")
    if ( uri ) {
        return uri.last().get("uri")
    }
}

def clone_k8s_repo () {
    def k8s_repo_url = "ssh://${GERRIT_NAME}@${GERRIT_HOST}:${GERRIT_PORT}/${GERRIT_PROJECT}.git"
    sshagent (credentials: ['mcp-ci-gerrit']) {
        withEnv(["GIT_K8S_CACHE_DIR=${git_k8s_cache_dir}",
                 "GIT_K8S_REPO_URL=${k8s_repo_url}"]) {
            sh '''
                git clone file://${GIT_K8S_CACHE_DIR} .
                git remote add kubernetes ${GIT_K8S_REPO_URL}
                git reset --hard
                if ! git clean -x -f -d -q ; then
                  sleep 1
                  git clean -x -f -d -q
                fi
                git fetch kubernetes --tags
            '''
        }
    }
}

def upload_image_to_artifactory (registry, image, version, repository) {
    def server = Artifactory.server('mcp-ci')
    def artDocker
    withCredentials([
            [$class: 'UsernamePasswordMultiBinding',
             credentialsId: 'artifactory',
             passwordVariable: 'ARTIFACTORY_PASSWORD',
             usernameVariable: 'ARTIFACTORY_LOGIN']
    ]) {
        sh ("docker login -u ${ARTIFACTORY_LOGIN} -p ${ARTIFACTORY_PASSWORD} ${registry}")
        sh ("docker push ${registry}/${image}:${version}")
        //artDocker = Artifactory.docker("${env.ARTIFACTORY_LOGIN}", "${env.ARTIFACTORY_PASSWORD}")
    }

    //artDocker.push("${registry}/${image}:${version}", "${repository}")
    def image_url = "${env.ARTIFACTORY_URL}/api/storage/${repository}/${image}/${version}"
    def properties = ['com.mirantis.build_name':"${env.JOB_NAME}",
                      'com.mirantis.build_id': "${env.BUILD_NUMBER}",
                      'com.mirantis.changeid': "${env.GERRIT_CHANGE_ID}",
                      'com.mirantis.patchset_number': "${env.GERRIT_PATCHSET_NUMBER}",
                      'com.mirantis.target_tag': "${version}"]

    set_properties(image_url, properties)
}

def upload_binaries_to_artifactory (uploadSpec, publish_info=false) {
    def server = Artifactory.server('mcp-ci')
    buildInfo.append(server.upload(uploadSpec))

    if ( publish_info ) {
        buildInfo.env.capture = true
        buildInfo.env.filter.addInclude("*")
        buildInfo.env.filter.addExclude("*PASSWORD*")
        buildInfo.env.filter.addExclude("*password*")
        buildInfo.env.collect()

        server.publishBuildInfo(buildInfo)
    }

}

def generate_git_version () {
    def current_tag = sh(script: "git describe --abbrev=0 --tags", returnStdout: true).trim()
    def commit_num = sh(script: "git rev-list ${current_tag}..HEAD --count", returnStdout: true).trim()
    if (commit_num == "0") {
        return "${current_tag}"
    } else {
        return "${current_tag}-${commit_num}"
    }
}