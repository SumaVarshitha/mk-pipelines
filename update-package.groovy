/**
 * Update packages on given nodes
 *
 * Expected parameters:
 *   SALT_MASTER_CREDENTIALS    Credentials to the Salt API.
 *   SALT_MASTER_URL            Full Salt API address [https://10.10.10.1:8000].
 *   TARGET_SERVERS             Salt compound target to match nodes to be updated [*, G@osfamily:debian].
 *   TARGET_PACKAGES            Space delimited list of packages to be updates [package1=version package2=version], empty string means all updating all packages to the latest version.
 *   TARGET_SIZE_TEST           Number of nodes to list package updates, empty string means all targetted nodes.
 *   TARGET_SIZE_SAMPLE         Number of selected noded to live apply selected package update.
 *   TARGET_SIZE_BATCH          Batch size for the complete live package update on all nodes, empty string means apply to all targetted nodes.
 *
**/

def common = new com.mirantis.mk.Common()
def salt = new com.mirantis.mk.Salt()

def saltMaster
def targetAll = ['expression': TARGET_SERVERS, 'type': 'compound']
def targetTestSubset
def targetLiveSubset
def targetLiveAll
def minions
def result
def command
def packages

node() {
    try {

        if (TARGET_PACKAGES != "") {
            command = "pkg.install"
            packages = TARGET_PACKAGES.tokenize(' ')
        }
        else {
            command = "pkg.upgrade"
            packages = null
        }

        stage('Connect to Salt master') {
            saltMaster = salt.connection(SALT_MASTER_URL, SALT_MASTER_CREDENTIALS)
        }

        stage('List target servers') {
            minions = salt.getMinions(saltMaster, targetAll)
            if (TARGET_SUBSET_TEST != "") {
                targetTestSubset = minions.subList(0, Integer.valueOf(TARGET_SUBSET_TEST)).join(' or ')
            }
            else {
                targetTestSubset = minions.join(' or ')
            }
            targetLiveSubset = minions.subList(0, Integer.valueOf(TARGET_SUBSET_LIVE)).join(' or ')
            targetLiveAll = minions.join(' or ')
            common.infoMsg("Found nodes: ${targetLiveAll}")
            common.infoMsg("Selected test nodes: ${targetTestSubset}")
            common.infoMsg("Selected sample nodes: ${targetLiveSubset}")
        }

        stage("List package upgrades") {
            salt.runSaltProcessStep(saltMaster, targetTestSubset, 'pkg.list_upgrades', [], null, true)
        }

        stage('Confirm live package upgrades on sample') {
            if(TARGET_PACKAGES==""){
                timeout(time: 2, unit: 'HOURS') {
                    def userInput = input(
                     id: 'userInput', message: 'Insert package names for update', parameters: [
                     [$class: 'TextParameterDefinition', defaultValue: '', description: 'Package names (or *)', name: 'packages']
                    ])
                    if(userInput['packages'] != ""){
                        packages = userInput['packages'].tokenize(" ")
                    }
                }
            }else{
                timeout(time: 2, unit: 'HOURS') {
                   input message: "Approve live package upgrades on ${targetLiveSubset} nodes?"
                }
            }
        }

        stage('Apply package upgrades on sample') {
            salt.runSaltProcessStep(saltMaster, targetLiveSubset, command, packages, null, true)

        }

        stage('Confirm package upgrades on all nodes') {
            timeout(time: 2, unit: 'HOURS') {
               input message: "Approve live package upgrades on ${targetLiveAll} nodes?"
            }
        }

        stage('Apply package upgrades on all nodes') {
            salt.runSaltProcessStep(saltMaster, targetLiveAll, command, packages, null, true)
        }

    } catch (Throwable e) {
        // If there was an error or exception thrown, the build failed
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        // common.sendNotification(currentBuild.result,"",["slack"])
    }
}