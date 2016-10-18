def tools = new ci.mcp.Tools()
def buildInfo = Artifactory.newBuildInfo()
def properties = tools.getBinaryBuildProperties()
def server = Artifactory.server('mcp-ci')

node('builder') {

  // defines
  def distro = "${DISTRO}"
  def packer_log = 1
  def arch = "amd64"
  def headless = true

  stage('Code checkout') {
    gerritPatchsetCheckout {
      credentialsId = "mcp-ci-gerrit"
      withWipeOut = true
    }
  }
  dir('utils/packer') {
    stage('Prepare packer') {
      sh '''
        curl https://releases.hashicorp.com/packer/0.10.2/packer_0.10.2_linux_amd64.zip -o packer.zip
        unzip packer.zip
        rm packer.zip
      '''
    }

    stage('Build the image') {
      if(distro =~ /^ubuntu.*amd64/) {
        withEnv(["PACKER_LOG=${packer_log}",
                 "HEADLESS=${headless}",
                 "ARCH=${arch}",
                 "DISTRO=${distro}",
                 "WORKSPACE=${env.WORKSPACE}",
                 "UBUNTU_TYPE=server" ]) {
          sh '''
            export TIME="$(date +%s)"
            export UBUNTU_MAJOR_VERSION="$(echo ${DISTRO} | cut -f1-2 -d '.'| sed 's/[^0-9.]*\\([0-9.]*\\).*/\\1/')"
            export UBUNTU_MINOR_VERSION=".$(echo ${DISTRO} | cut -f3 -d '.' | sed 's/[^0-9.].*//')"
            ./packer build -only=qemu ubuntu.json
            mkdir ${WORKSPACE}/output-images/
            mv ${DISTRO}.qcow2 ${WORKSPACE}/output-images/${DISTRO}-${TIME}.qcow2
          '''
        }
      }
    }
  }

  dir('output-images') {
    stage('Publish the qcow2 image') {
      def uploadSpec = """{
        "files": [
                {
                    "pattern": "*.qcow2",
                    "target": "vm-images/packer/",
                    "props": "${properties}"
                }
            ]
        }"""
      server.upload(uploadSpec, buildInfo)
      server.publishBuildInfo buildInfo
      sh "rm *.qcow2"
    }
  }
}
