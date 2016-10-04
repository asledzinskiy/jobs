coverage = "n"
artifacts_dir = "_artifacts"
git_k8s_cache_dir = "/home/jenkins/kubernetes"
event = env.GERRIT_EVENT_TYPE
git_commit_tag_id = ''
timestamp = System.currentTimeMillis().toString()
kube_docker_registry = env.KUBE_DOCKER_REGISTRY
kube_docker_repo = 'hyperkube-amd64'
artifactory_dev_repo = "mcp-k8s-ci"
artifactory_prod_repo = "mcp-k8s"
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
            update_k8s_mirror()
            deleteDir()
            git url: "${git_k8s_cache_dir}"
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
    stage('integration-tests') {
        def docker_image_int = "${env.DOCKER_IMAGE_INTEGRATION}"
        parallel integration: {
            node ('k8s') {
                update_k8s_mirror()
                deleteDir()
                git url: "${git_k8s_cache_dir}"
                gerritPatchsetCheckout{
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

        }, conformance: {
            node ('k8s-e2e') {

                def k8s_repo_dir = "${env.WORKSPACE}/kubernetes"

                update_k8s_mirror()
                deleteDir()
                sh "mkdir ${env.WORKSPACE}/${artifacts_dir}"
                dir("${k8s_repo_dir}") {
                    git url: "${git_k8s_cache_dir}"
                    gerritPatchsetCheckout{
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
        failFast: true
    }
}

def build_publish_binaries () {
    stage('hyperkube-build') {
        node('k8s') {
            update_k8s_mirror()
            deleteDir()


            def calico_cni = env.CALICO_CNI
            def calico_ipam = env.CALICO_IPAM
            def k8s_repo_dir = "${env.WORKSPACE}/kubernetes"

            def kube_docker_owner = 'jenkins'
            def registry = "${kube_docker_registry}/${kube_docker_owner}"

            if ( ! kube_docker_registry ) {
                error('KUBE_DOCKER_REGISTRY must be set')
            }
            if ( ! kube_docker_owner ) {
                error('KUBE_DOCKER_OWNER must be set')
            }
            if ( ! calico_cni ) {
                calico_cni = "https://github.com/projectcalico/calico-cni/releases/download/v1.3.1/calico"
            }
            if ( ! calico_ipam ) {
                calico_ipam = "https://github.com/projectcalico/calico-cni/releases/download/v1.3.1/calico-ipam"
            }

            dir("${k8s_repo_dir}") {
                git url: "${git_k8s_cache_dir}"
                gerritPatchsetCheckout{
                    credentialsId = "mcp-ci-gerrit"
                }
                git_commit_tag_id = sh(script: "git describe | sed 's/\\-[^-]*\$//'", returnStdout: true).trim()

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
                         "KUBE_DOCKER_REPOSITORY=${kube_docker_repo}",
                         "KUBE_DOCKER_OWNER=${kube_docker_owner}",
                         "ARTIFACTORY_USER_EMAIL=jenkins@mcp-ci-artifactory",
                         "KUBE_DOCKER_REGISTRY=${kube_docker_registry}",
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
                                  com.mirantis.image-specs.build_url="${BUILD_URL}" \
                                  com.mirantis.image-specs.patchset="${GERRIT_PATCHSET_REVISION}"
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
                        writeFile file: "hyperkube_image_${env.VERSION}.yaml",
                                text: """
                                      hyperkube_image_repo: ${env.KUBE_DOCKER_REGISTRY}/${env.KUBE_DOCKER_REPOSITORY}
                                      hyperkube_image_tag: ${env.KUBE_DOCKER_VERSION}
                                      gerrit_change_url: ${env.GERRIT_CHANGE_URL}
                        """.stripIndent()

                        stage('publish') {
                            def uploadSpec = """{
                                  "files": [
                                    {
                                      "pattern": "hyperkube*.yaml",
                                      "target": "${artifactory_dev_repo}/images-info/"
                                    },
                                    {
                                      "pattern": "artifacts/hyperkube**",
                                      "target": "${artifactory_dev_repo}/hyperkube-binaries/"
                                    }
                                  ]
                            }"""
                            upload_binaries_to_artifactory(uploadSpec)
                            upload_image_to_artifactory ("${kube_docker_registry}", "${kube_docker_repo}", "${kube_docker_version}", "${artifactory_dev_repo}")
                            currentBuild.description = "${kube_docker_registry}/${kube_docker_repo}:${kube_docker_version}"
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
}

def run_system_test () {
    stage('system-tests') {
        build job: 'calico.system-test.deploy',
                parameters: [
                        string(name: 'HYPERKUBE_IMAGE_TAG', value: "${git_commit_tag_id}_${timestamp}"),
                        string(name: 'HYPERKUBE_IMAGE_REPO', value: "${kube_docker_registry}/${kube_docker_repo}"),
                        string(name: 'GERRIT_CHANGE_URL', value: "${env.GERRIT_CHANGE_URL}")]
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
                        'targetRepo' : artifactory_prod_repo.toString()]
                // promote build artifacts except docker because of jfrog bug
                server.promote promotionConfig
                // promote docker image
                promote_docker_artifact(env.ARTIFACTORY_URL,
                        artifactory_dev_repo,
                        artifactory_prod_repo,
                        kube_docker_repo,
                        buildInfo.get('com.mirantis.target_tag').join(','),
                        buildInfo.get('com.mirantis.target_tag').join(',').split("_")[0],
                        true)
                promote_docker_artifact(env.ARTIFACTORY_URL,
                        artifactory_dev_repo,
                        artifactory_prod_repo,
                        kube_docker_repo,
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

def promote_docker_artifact(artifactory_url, artifactory_dev_repo, artifactory_prod_repo,
                            docker_repo, artifact_tag, target_tag, copy=false) {
    def url = "${artifactory_url}/api/docker/${artifactory_dev_repo}/v2/promote"
    withCredentials([
            [$class: 'UsernamePasswordMultiBinding',
             credentialsId: 'artifactory',
             passwordVariable: 'ARTIFACTORY_PASSWORD',
             usernameVariable: 'ARTIFACTORY_LOGIN']
    ]) {
        writeFile file: "query.json",
                text: """{
                  \"targetRepo\": \"${artifactory_prod_repo}\",
                  \"dockerRepository\": \"${docker_repo}\",
                  \"tag\": \"${artifact_tag}\",
                  \"targetTag\" : \"${target_tag}\",
                  \"copy\": \"${copy}\"
              }""".stripIndent()
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

def update_k8s_mirror () {
    def k8s_repo_url = "ssh://${GERRIT_NAME}@${GERRIT_HOST}:${GERRIT_PORT}/${GERRIT_PROJECT}.git"
    dir(git_k8s_cache_dir) {
        git credentialsId: 'mcp-ci-gerrit', url: "${k8s_repo_url}"
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
