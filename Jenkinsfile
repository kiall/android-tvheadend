#!/usr/bin/env groovy

/* Only keep the 10 most recent builds. */
def projectProperties = [
    [$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '5']],
    [$class: 'PipelineTriggersJobProperty', triggers: [
        [$class: 'GitHubPushTrigger']
    ]],
    [$class: 'DisableConcurrentBuildsJobProperty'],
]

properties(projectProperties)

node ('android-slave'){
    stage('Preparation') {
        step([$class: 'WsCleanup'])
        checkout scm
        sh 'env > env-dump'
    }
    stage('Assemble') {
        withCredentials([[$class: 'FileBinding', credentialsId: 'android-keystore-tvheadend', variable: 'ANDROID_KEYSTORE'], [$class: 'StringBinding', credentialsId: 'android-keystore-tvheadend-password', variable: 'ANDROID_KEYSTORE_PASSWORD']]) {
            writeFile file: 'keystore.properties', text: "storeFile=$ANDROID_KEYSTORE\nstorePassword=$ANDROID_KEYSTORE_PASSWORD\nkeyAlias=Kiall Mac Innes\nkeyPassword=$ANDROID_KEYSTORE_PASSWORD\n"
            sh './gradlew assemble'
        }
    }
    stage('Archive APK') {
        archiveArtifacts artifacts: 'app/build/outputs/apk/*.apk', fingerprint: true
        stash includes: 'app/build/outputs/apk/*.apk', name: 'built-apk'
    }
    stage('Lint') {
        sh './gradlew lint'
        androidLint canComputeNew: false, canRunOnFailed: true, defaultEncoding: '', healthy: '', pattern: '**/lint-results*.xml', unHealthy: ''
    }
    stage('Publish') {
        def lastTag = sh(returnStdout: true, script: "git describe --tags --abbrev=0").trim()
        def changeLog = sh(returnStdout: true, script: "git log $lastTag..HEAD --oneline | grep -v 'Merge pull request' | cut -d' ' -f2- | sed -e 's/^/* /'").trim()

        androidApkUpload(
            apkFilesPattern: 'app/build/outputs/apk/ie.macinnes.tvheadend_*-release.apk',
            googleCredentialsId: 'android-tvheadend',
            trackName: 'alpha',
            recentChangeList: [
                [language: 'en-GB', text: changeLog],
            ],
        )
    }
}
