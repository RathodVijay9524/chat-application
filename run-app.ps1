Write-Host "Setting Java 17 environment..." -ForegroundColor Green
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.5"
$env:PATH = "C:\Program Files\Java\jdk-17.0.5\bin;" + $env:PATH

Write-Host "Setting API keys..." -ForegroundColor Green
$env:OPENAI_API_KEY = "test-key"
$env:CLAUDAI_API_KEY = "test-key"
$env:OPENROUTER_API_KEY = "test-key"
# $env:GROQ_API_KEY = "test-key"  # Using your real Groq API key from environment
$env:HUGGINGFACE_API_KEY = "test-key"
$env:GEMINI_API_KEY = "test-key"

Write-Host "Java version:" -ForegroundColor Yellow
java -version

Write-Host "`nStarting Spring Boot application..." -ForegroundColor Green
./mvnw spring-boot:run
