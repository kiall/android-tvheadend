import jenkins.model.*

def assemble() {
    sh './gradlew assemble -PbuildNumber=' + env.BUILD_NUMBER
}

def archive() {
    archiveArtifacts artifacts: 'app/build/outputs/apk/*.apk', fingerprint: true
    stash includes: 'app/build/outputs/apk/*.apk', name: 'built-apk'
}

def lint() {
    sh './gradlew lint -PbuildNumber=' + env.BUILD_NUMBER
    androidLint canComputeNew: false, canRunOnFailed: true, defaultEncoding: '', healthy: '', pattern: '**/lint-results*.xml', unHealthy: ''
}

def publishApkToStore(String trackName) {
    withCredentials([
        [$class: 'FileBinding', credentialsId: 'android-keystore-tvheadend', variable: 'ANDROID_KEYSTORE'],
        [$class: 'StringBinding', credentialsId: 'android-keystore-tvheadend-password', variable: 'ANDROID_KEYSTORE_PASSWORD'],
        [$class: 'FileBinding', credentialsId: 'android-playserviceaccount-tvheadend', variable: 'ANDROID_PLAY_SERVICE_ACCOUNT'],
    ]) {
        writeFile file: 'local-tvheadend.properties', text: "ie.macinnes.tvheadend.keystoreFile=$ANDROID_KEYSTORE\nie.macinnes.tvheadend.keystorePassword=$ANDROID_KEYSTORE_PASSWORD\nie.macinnes.tvheadend.keyAlias=Kiall Mac Innes\nie.macinnes.tvheadend.keyPassword=$ANDROID_KEYSTORE_PASSWORD\nie.macinnes.tvheadend.playServiceAccountFile=$ANDROID_PLAY_SERVICE_ACCOUNT\n"

        // Publish everything when doing a production release
        if (trackName == 'production') {
          sh './gradlew publishRelease -PbuildNumber=' + env.BUILD_NUMBER + ' -PplayStoreTrack=' + trackName
        } else {
          sh './gradlew publishApkRelease -PbuildNumber=' + env.BUILD_NUMBER + ' -PplayStoreTrack=' + trackName
        }
    }
}

def publishApkToGitHub() {
    def tagName = sh(returnStdout: true, script: "git describe --tags --abbrev=0 --exact-match").trim()
    def changeLog = sh(returnStdout: true, script: "./tools/generate-changelog").trim().replaceAll(~/'/, "\'")

    withCredentials([
        [$class: 'StringBinding', credentialsId: 'github-pat-kiall', variable: 'GITHUB_TOKEN'],
    ]) {
        sh(script: "github-release release --user kiall --repo android-tvheadend --tag ${tagName} --name ${tagName} --description '${changeLog}'")
        sh(script: "github-release upload --user kiall --repo android-tvheadend --tag ${tagName} --name ie.macinnes.tvheadend_${tagName}-release.apk --file app/build/outputs/apk/ie.macinnes.tvheadend-release.apk")
    }
}

def withGithubNotifier(Closure<Void> job) {
   notifyGithub('STARTED')
   catchError {
      currentBuild.result = 'SUCCESS'
      job()
   }
   notifyGithub(currentBuild.result)
}
 
def notifyGithub(String result) {
   switch (result) {
      case 'STARTED':
         setGitHubPullRequestStatus(context: env.JOB_NAME, message: "Build started", state: 'PENDING')
         break
      case 'FAILURE':
         setGitHubPullRequestStatus(context: env.JOB_NAME, message: "Build error", state: 'FAILURE')
         break
      case 'UNSTABLE':
         setGitHubPullRequestStatus(context: env.JOB_NAME, message: "Build unstable", state: 'FAILURE')
         break
      case 'SUCCESS':
         setGitHubPullRequestStatus(context: env.JOB_NAME, message: "Build finished successfully", state: 'SUCCESS')
         break
   }
}

return this;
