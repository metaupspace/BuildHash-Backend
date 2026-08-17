
## 16:42 | main
Moved root-level config files (OpenApiConfig, RabbitConfig, SecurityConfig) to infra/config/, deleted old package dirs (auth/, user/, tax/, catalog/, device/, loginhistory/, root config/), verified via full repo sweep—but found exception handler files in common/ unmoved per original mapping (hexagonal migration incomplete, files still to move, test run & PROGRESS update pending).
## 16:49 | main
Moved domain exception hierarchy to domain/exception/, ApiError to api/dto/response/, GlobalExceptionHandler to api/ (w/ HTTP status mapping), updated 24 importing files, deleted old common/exception, ran full test suite, updated PROGRESS.md.