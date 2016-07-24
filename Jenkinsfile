node ('debian-android') {
  stage 'Checkout Code'
  checkout scm

  stage 'Build'
  sh './gradlew build connectedCheck'
}
