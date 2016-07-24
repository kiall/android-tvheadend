node ('debian-android') {
  stage 'Checkout'
  checkout scm

  stage 'Build'
  sh './gradlew build connectedCheck'

  stage 'Publish'
  sh 'echo Publish'
}
