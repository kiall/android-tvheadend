node ('debian-android') {
  stage 'Checkout Code'
  checkout scm

  stage 'Build'
  sh './gradlew build connectedCheck'

  stage 'Publish'
  sh 'echo Publish'
}
