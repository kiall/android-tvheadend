#!/usr/bin/env groovy

def projectProperties = [
    [$class: 'PipelineTriggersJobProperty', triggers: [
        [$class: 'GitHubPushTrigger']
    ]],
    [$class: 'DisableConcurrentBuildsJobProperty'],
]

properties(projectProperties)

node ('android-slave') {
    stage('Preparation') {
        step([$class: 'WsCleanup'])
        checkout scm
    }

    def common = load 'Jenkinsfile.groovy'

    stage('Assemble') {
        withCredentials([
            [$class: 'FileBinding', credentialsId: 'android-keystore-tvheadend', variable: 'ANDROID_KEYSTORE'],
            [$class: 'StringBinding', credentialsId: 'android-keystore-tvheadend-password', variable: 'ANDROID_KEYSTORE_PASSWORD'],
            [$class: 'StringBinding', credentialsId: 'acra-report-uri-tvheadend', variable: 'ACRA_REPORT_URI'],
        ]) {
            writeFile file: 'keystore.properties', text: "storeFile=$ANDROID_KEYSTORE\nstorePassword=$ANDROID_KEYSTORE_PASSWORD\nkeyAlias=Kiall Mac Innes\nkeyPassword=$ANDROID_KEYSTORE_PASSWORD\n"
            writeFile file: 'acra.properties', text: "report_uri=$ACRA_REPORT_URI\n"

            common.assemble()
        }
    }
    stage('Lint') {
        common.lint()
    }
    stage('Archive APK') {
        common.archive()
    }
    stage('Publish') {
        parallel (
            playStore: {
                common.publishApkToStore('beta')
            },
            githubRelease: {
                common.publishApkToGitHub()
            }
        )
    }
}
