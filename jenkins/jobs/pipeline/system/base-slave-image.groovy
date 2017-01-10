import java.net.URI

docker_dev_repo = "docker-dev-local"
docker_prod_repo = "docker-prod-local"
namespace = 'mirantis/jenkins-slave-images'
// Docker files will be stored in these folders:
image_names = [ "debian-slave", "ubuntu-slave" ]

artifactory = new com.mirantis.mcp.MCPArtifactory()
artifactoryServer = Artifactory.server("mcp-ci")
common = new com.mirantis.mcp.Common()
gitTools = new com.mirantis.mcp.Git()
buildInfo = Artifactory.newBuildInfo()
docker_registry = env.DOCKER_REGISTRY

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
            gitTools.gerritPatchsetCheckout{
                credentialsId = "mcp-ci-gerrit"
            }
            def dockerRepository = "${env.DOCKER_REGISTRY}"
            def artifactory_url = artifactoryServer.getUrl()
            def uri = new URI(artifactory_url);
            def artifactory_host = uri.getHost();
            def imageTag = common.getDatetime()

            for(img in image_names) {
                // Build
                def base_image_version = sh(script: "cat ${img}/Dockerfile | grep -E '^FROM .*' | \
                                                     awk -F ':' '{print \$NF}'",
                        returnStdout: true).trim()
                def old_registry = sh(script: "cat ${img}/Dockerfile | \
                                           sed -rn 's/^FROM +([^\\/]+)\\/.*\$/\\1/p'",
                        returnStdout: true).trim()
                def docker_image = "${namespace}/${img}-${base_image_version}"
                // Build from local registry
                sh "sed -i 's/${old_registry}/${docker_registry}/g' ${img}/Dockerfile"
                // Remove old one if exists
                sh "docker rmi -f ${docker_image} || true"
                sh "docker build --build-arg artifactory_host=${artifactory_host} -t ${docker_image}:${imageTag} ${img}"

                // Upload
                sh "docker tag ${docker_image}:${imageTag} ${dockerRepository}/${docker_image}:${imageTag}"
                artifactory.uploadImageToArtifactory(artifactoryServer,
                                                     dockerRepository,
                                                     "${docker_image}",
                                                     "${imageTag}",
                                                     docker_dev_repo)

                // Add custom properties
                def release_imageTag = sh(returnStdout: true, script: 'git rev-list --count HEAD').trim()
                def custom_properties = ['com.mirantis.baseImageVersion': "${base_image_version}",
                                         'com.mirantis.releaseImageTag': "${release_imageTag}",
                                         'com.mirantis.imageType': "${img}"]
                def artifact_url = "${artifactory_url}/api/storage/${docker_dev_repo}/${docker_image}/${imageTag}"
                artifactory.setProperties(artifact_url, custom_properties, true)

                // Cleanup
                sh "docker rmi ${docker_image}:${imageTag} ${dockerRepository}/${docker_image}:${imageTag}"
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
                def properties = ['com.mirantis.gerritChangeId': "${env.GERRIT_CHANGE_ID}",
                    'com.mirantis.gerritPatchsetNumber': "${env.GERRIT_PATCHSET_NUMBER}",
                    'com.mirantis.imageType': "${img}"]

                // Search for an artifact with required properties
                def artifact_url = artifactory.uriByProperties(artifactory_url, properties)

                def img_properties = artifactory.getPropertiesForArtifact(artifact_url)
                if ( artifact_url ) {
                    def base_image_version = img_properties.get('com.mirantis.baseImageVersion').join(',')
                    def docker_image = "${namespace}/${img}-${base_image_version}"

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
                            img_properties.get('com.mirantis.releaseImageTag').join(','),
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
