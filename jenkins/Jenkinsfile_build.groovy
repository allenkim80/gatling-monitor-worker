package jenkins
/*
    If you want to run this script, you'll need to install proper git plugin to trigger build from push.
    Here we get the source code from GitLab and use GitLab plugin.
    http://ci.edge.sphd.io/git/notifyCommit?url=git@git.sphd.io:qe/nfs-edge-loadtest-preob.git
*/
node ('APSEO-QE-LG1') {
    def defaultBranch = "develop"
    def defaultImageTag = "latest"
    def defaultImageName = "gatling-monitor-worker"
    def defaultSlackChannel = "#sphd-qe-notification"
    def defaultEmailRecipient = "EAK.QE@ea.com"
    def defaultNexonRepoAddress = "d-nx-betago.sphd.io/betago/"
    def defaultAWSRepoAddress = "490796368613.dkr.ecr.ap-northeast-1.amazonaws.com"

    // Define properties which will be used for each environments
    properties([
            buildDiscarder(
                    logRotator(
                            artifactDaysToKeepStr: '',
                            artifactNumToKeepStr: '',
                            daysToKeepStr: '60',
                            numToKeepStr: '100'
                    )
            ),
            parameters([
                    string(
                            defaultValue: defaultBranch,
                            description: 'Input branch of gatling load generator src.',
                            name: 'branchName'
                    ),
                    string(
                            defaultValue: defaultImageTag,
                            description: 'Input tag of gatling load generator image.',
                            name: 'imageTag'
                    ),
                    // Need to add or remove fo notification channel for this test
                    string(
                            defaultValue: defaultSlackChannel,
                            description: 'Input slack channels to receive notification from build. (comma separated)',
                            name: 'SlackRecipients'
                    ),
                    // Need to add or remove fo notification recipient for this test
                    string(
                            defaultValue: defaultEmailRecipient,
                            description: 'Input email addresses to receive notification from build. (semicolon separated)',
                            name: 'EmailRecipients'
                    )
            ]),
            pipelineTriggers([
                    // Need to change pipeline triggers if this build need to be triggered by upstream project
                    pollSCM('')
                    /*
                    GitLabPushTrigger(
                            triggerOnPush: true,
                            triggerOnMergeRequest: true,
                            secretToken: 'b689b9750f8abce4eb6ebceafe63da96'
                    )
                    upstream(
                            threshold: hudson.model.Result.SUCCESS,
                            upstreamProjects: upstreamProject
                    )
                    */
            ])
    ])

    git branch: '${branchName}', credentialsId: 'a6ead784-e289-457a-80db-107b8d5b6d25', url : 'http://git.sphd.io/qe/gatling-monitor-worker.git'

    def mvnHome

    stage('Preparation') {
        // Get the Maven tool
        // This 'Maven3' Maven tool must be configured in the global Tool configuration with name 'Maven3' in Jenkins
        mvnHome = tool 'Maven3'

        echo "Current workspace : ${workspace}, branch : ${branchName}, tag : ${imageTag}"

        sh "echo ${USER}"

        echo "Log in apseo-nexus docker registry"
        sh "docker login -u eass-build -p 'Welcome2ea!!' apseo-nexus:8082"
        sh "docker login -u eass-build -p 'Welcome2ea!!' apseo-nexus:8083"

        echo "Log in AWS EC Container Service for connecting EC Repository"
        sh "aws --version"
        sh "cd docker && echo 'Welcome2ea!!' | sudo -S -k sh ./aws_ecs_login.sh"
    }

    stage('Build worker') {
        echo "Build sbt assembly"

        sh "sbt clean assembly"
    }

    stage('Clean Image') {
        try {
            echo "Delete local docker images........"
            sh "docker rmi -f ${defaultImageName}"


            echo "Delete an AWS ECS Repository's latest image....."
            sh "aws ecr batch-delete-image --repository-name sphd/qe/${defaultImageName} --image-ids imageTag=latest"
        } catch (error) {
            echo error.message
        }
    }

    stage('Build Image') {
        try {
            sh "docker build --build-arg BRANCH_NAME=${branchName} --no-cache=true -t ${defaultImageName} ."
        } catch(error) {
            echo error.message

            notifyFailure(
                    "${JOB_NAME}",
                    "${JOB_URL}",
                    "${BUILD_NUMBER}",
                    "${BUILD_URL}",
                    "${SlackRecipients}",
                    "${EmailRecipients}",
                    "Push",
                    "Docker build failed."
            )

            throw error
        }
    }

    stage('Tagging and Push to Local') {
        echo "Tagging and push a build image to local registry"
        try {
            sh "docker tag ${defaultImageName}:latest apseo-nexus:8083/qe/${defaultImageName}:${imageTag}"
            sh "docker push apseo-nexus:8083/qe/${defaultImageName}:${imageTag}"
        } catch(error) {
            echo error.message

            notifyFailure(
                    "${JOB_NAME}",
                    "${JOB_URL}",
                    "${BUILD_NUMBER}",
                    "${BUILD_URL}",
                    "${SlackRecipients}",
                    "${EmailRecipients}",
                    "Push",
                    "Docker build failed."
            )

            throw error
        }

    }

    stage('Tagging and Push to Nexon') {
        echo "Tagging and push a buuild image to Nexon registry"

        try {
            sh "docker tag ${defaultImageName}:latest ${defaultNexonRepoAddress}${defaultImageName}:${imageTag}"
            sh "docker push ${defaultNexonRepoAddress}${defaultImageName}:${imageTag}"
        } catch (error) {
            echo error.message

            notifyFailure(
                    "${JOB_NAME}",
                    "${JOB_URL}",
                    "${BUILD_NUMBER}",
                    "${BUILD_URL}",
                    "${SlackRecipients}",
                    "${EmailRecipients}",
                    "Push",
                    "Docker push failed."
            )

            throw error
        }

        notifySuccessful(
                "${JOB_NAME}",
                "${JOB_URL}",
                "${BUILD_NUMBER}",
                "${BUILD_URL}",
                "${SlackRecipients}",
                "${EmailRecipients}"
        )
    }

    stage('Tagging and Push to AWS') {
        echo "Tagging and push a build image to AWS registry"

        try {
            sh "docker tag ${defaultImageName}:latest ${defaultAWSRepoAddress}/sphd/qe/${defaultImageName}:${imageTag}"
            sh "docker push ${defaultAWSRepoAddress}/sphd/qe/${defaultImageName}:${imageTag}"
        } catch (error) {
            echo error.message
            echo "Please check whether AWS instance is running or not."
            echo "If instances are is OK, please check insecure registry of a slave machine."

            notifyFailure(
                    "${JOB_NAME}",
                    "${JOB_URL}",
                    "${BUILD_NUMBER}",
                    "${BUILD_URL}",
                    "${SlackRecipients}",
                    "${EmailRecipients}",
                    "Push",
                    "Docker build failed."
            )

            throw error
        }

        notifySuccessful(
                "${JOB_NAME}",
                "${JOB_URL}",
                "${BUILD_NUMBER}",
                "${BUILD_URL}",
                "${SlackRecipients}",
                "${EmailRecipients}"
        )
    }
}

def notifyStarted(jobName, jobURL, buildNumber, buildUrl, slackRecipients, emailRecipients, upstreamBuildTrigger) {
    def triggerMessage = upstreamBuildTrigger ? "(triggered by upstream)" : "(triggered by user)"
    def slackMessage = "STARTED : '${jobName} [${buildNumber}]' ${triggerMessage} (${buildUrl})"
    def emailSubject = "STARTED : '${jobName} [${buildNumber}]' ${triggerMessage}"
    def emailMessage = "<p>STARTED: '${jobName} [${buildNumber}] ${triggerMessage}' (<a href='${jobURL}'>Pipeline view</a>)</p> <p>Check console output at &QUOT;<a href='${buildUrl}'>${jobName} [${buildNumber}]</a>&QUOT;</p>"

    echo "slack recipients : ${slackRecipients}"

    slackSend (
            channel: slackRecipients,
            color: '#FFFF00',
            message: slackMessage,
            teamDomain: 'electronic-arts',
            token: '4tPqpKAsJTLiibcXhnU7bdXC'
    )

    emailext(
            subject: emailSubject,
            body: emailMessage,
            mimeType: 'text/html',
            to: emailRecipients
    )
}

def notifyFailure(jobName, jobURL, buildNumber, buildUrl, slackRecipients, emailRecipients, phase, reason) {
    def slackMessage = "FAILED(${reason}) : '${jobName} [${buildNumber}]' (branch:${branchName}, image:${imageTag}) (<${buildUrl}|Open>)"
    def emailSubject = "FAILED(${reason}) : '${jobName} [${buildNumber}]'"
    def emailMessage = "<p>FAILED(${reason}) : '${jobName} [${buildNumber}]' (<a href='${jobURL}'>Pipeline view</a>)</p> <p>Check console output at &QUOT;<a href='${buildUrl}'>${jobName} [${buildNumber}]</a>&QUOT;</p>"

    slackSend (
            channel: slackRecipients,
            color: '#FF0000',
            message: slackMessage,
            teamDomain: 'electronic-arts',
            token: '4tPqpKAsJTLiibcXhnU7bdXC'
    )

    emailext(
            subject: emailSubject,
            body: emailMessage,
            mimeType: 'text/html',
            to: emailRecipients
    )
}

def notifySuccessful(jobName, jobURL, buildNumber, buildUrl, slackRecipients, emailRecipients) {
    def slackMessage = "SUCCESSFUL : '${jobName} [${buildNumber}]' (branch:${branchName}, image:${imageTag}) (<${buildUrl}|Open>) (Registry : http://apseo-nexus/#browse/browse:docker-private:v2%2Fqe%2Fgatling-monitor-worker)"
    def emailSubject = "SUCCESSFUL : '${jobName} [${buildNumber}]'"
    def emailMessage = "<p>SUCCESSFUL : '${jobName} [${buildNumber}]' (<a href='${jobURL}'>Pipeline view</a>)</p> <p>Check console output at &QUOT;<a href='${buildUrl}'>${jobName} [${buildNumber}]</a>&QUOT;</p>"
    emailMessage += "<p>Internal Registry : <a href='http://apseo-nexus/#browse/browse:docker-private:v2%2Fqe%2Fgatling-monitor-worker'>apseo-nexus repository/</a></p>"

    slackSend (
            channel: slackRecipients,
            color: '#00FF00',
            message: slackMessage,
            teamDomain: 'electronic-arts',
            token: '4tPqpKAsJTLiibcXhnU7bdXC'
    )

    emailext(
            subject: emailSubject,
            body: emailMessage,
            mimeType: 'text/html',
            to: emailRecipients
    )
}

def isUpstreamBuildTrigger() {
    def causes = currentBuild.rawBuild.getCauses()
    for (cause in causes) {
        if (cause.class.toString().contains("UpstreamCause")) {
            echo "This build is triggered by Upstream cause : " + cause.toString()

            return true
        } else {
            echo "This build is triggered by cause : " + cause.toString()
        }
    }

    return false
}
