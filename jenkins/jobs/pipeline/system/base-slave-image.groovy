docker_dev_repo = "docker-dev-local"
docker_prod_repo = "docker-prod-local"
namespace = 'mirantis/jenkins-slave-images'
// Docker files will be stored in these folders:
image_names = [ "debian-slave", "ubuntu-slave" ]

artifactory = new com.mirantis.mcp.MCPArtifactory()
artifactoryServer = Artifactory.server("mcp-ci")
common = new com.mirantis.mcp.Common()
buildInfo = Artifactory.newBuildInfo()

if ( env.GERRIT_EVENT_TYPE ) {
    if ( "${env.GERRIT_EVENT_TYPE}" == "change-merged" ) {
      promote_artifacts()
    } else {
      build_artifacts()
    }
}

def build_artifacts () {
    stage('build') {
        node('docker') {
            // Run build only on commit
            deleteDir()
            gerritPatchsetCheckout{
                credentialsId = "mcp-ci-gerrit"
            }
            def dockerRepository = "${env.DOCKER_REGISTRY}"
            def artifactory_url = artifactoryServer.getUrl()
            def fsroot = "${env.FSROOT}"
            def jenkins_swarm_client_version = "${env.JENKINS_SWARM_CLIENT_VERSION}"
            def container_user = "${env.CONTAINER_USER}"
            def imageTag = sh(returnStdout: true, script: 'git rev-list --count HEAD').trim()

            for(img in image_names) {
                // Build
                def base_image_version = sh(script: "cat ${img}/Dockerfile | grep -E '^FROM .*' | \
                                                     awk -F ':' '{print \$NF}'",
                        returnStdout: true).trim()
                def docker_image = "${namespace}/${img}-${base_image_version}"
                // Remove old one if exists
                sh "docker rmi -f ${docker_image} || true"
                sh "docker build --build-arg fsroot=${fsroot} \
                    --build-arg jenkins_swarm_client_version=${jenkins_swarm_client_version} \
                    --build-arg container_user=${container_user} \
                    --build-arg artifactory_url=${artifactory_url} -t ${docker_image} ${img}"

                // Upload
                sh "docker tag ${docker_image} ${dockerRepository}/${docker_image}:${imageTag}"
                artifactory.uploadImageToArtifactory(artifactoryServer,
                                                     dockerRepository,
                                                     "${docker_image}",
                                                     "${imageTag}",
                                                     docker_dev_repo)

                // Cleanup
                sh "docker rmi ${docker_image} ${dockerRepository}/${docker_image}:${imageTag}"
            }
            // buildInfo should be published only once
            artifactoryServer.publishBuildInfo(buildInfo)
        }
    }
}

def promote_artifacts () {
    stage('promote') {
        node('docker') {
            def artifactory_url = artifactoryServer.getUrl()

            for (img in image_names) {
                def base_image_version = sh(script: "cat ${img}/Dockerfile | grep -E '^FROM .*' | \
                                                     awk -F ':' '{print \$NF}'",
                        returnStdout: true).trim()
                def docker_image = "${namespace}/${img}-${base_image_version}"
                def properties = ['com.mirantis.gerritChangeId': "${env.GERRIT_CHANGE_ID}",
                    'com.mirantis.gerritPatchsetNumber': "${env.GERRIT_PATCHSET_NUMBER}",
                    'com.mirantis.targetImg': "${docker_image}"]

                // Search for an artifact with required properties
                def artifact_url = artifactory.uriByProperties(artifactory_url, properties)

                def img_properties = artifactory.getPropertiesForArtifact(artifact_url)
                if ( artifact_url ) {
                    def promotionConfig = [
                        'buildName'  : img_properties.get('com.mirantis.buildName').join(','),
                        'buildNumber': img_properties.get('com.mirantis.buildNumber').join(','),
                        'targetRepo' : docker_prod_repo.toString()]
                    artifactoryServer.promote promotionConfig

                    // copy to docker_prod_repo with the same tag
                    artifactory.promoteDockerArtifact(artifactory_url,
                            docker_dev_repo,
                            docker_prod_repo,
                            "${docker_image}",
                            img_properties.get('com.mirantis.targetTag').join(','),
                            img_properties.get('com.mirantis.targetTag').join(','),
                                                      true)
                    // move to docker_prod_repo with the 'latest' tag
                    artifactory.promoteDockerArtifact(artifactory_url,
                            docker_dev_repo,
                            docker_prod_repo,
                            "${docker_image}",
                            img_properties.get('com.mirantis.targetTag').join(','),
                            'latest')
                } else {
                    throw new RuntimeException("Artifacts were not found, nothing to promote")
                }
            }
        }
    }
}
