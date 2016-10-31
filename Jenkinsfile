node ('android-slave'){
    stage('Preparation') {
        step([$class: 'WsCleanup'])
        checkout scm
    }
    stage('Assemble') {
        withCredentials([[$class: 'FileBinding', credentialsId: 'android-keystore-tvheadend', variable: 'ANDROID_KEYSTORE'], [$class: 'StringBinding', credentialsId: 'android-keystore-tvheadend-password', variable: 'ANDROID_KEYSTORE_PASSWORD']]) {
            writeFile file: 'keystore.properties', text: "storeFile=$ANDROID_KEYSTORE\nstorePassword=$ANDROID_KEYSTORE_PASSWORD\nkeyAlias=Kiall Mac Innes\nkeyPassword=$ANDROID_KEYSTORE_PASSWORD\n"
            sh './gradlew assemble'
        }
    }
    stage('Lint') {
        sh './gradlew lint'
        androidLint canComputeNew: false, canRunOnFailed: true, defaultEncoding: '', healthy: '', pattern: '**/lint-results*.xml', unHealthy: ''
    }
    stage('Publish') {
        androidApkUpload apkFilesPattern: 'app/build/outputs/apk/ie.macinnes.tvheadend_*-release.apk', googleCredentialsId: 'android-tvheadend', trackName: 'alpha'
    }
}
