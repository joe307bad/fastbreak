# Use the official .NET 9.0 runtime as base image for final stage
FROM mcr.microsoft.com/dotnet/aspnet:9.0 AS base
WORKDIR /app
EXPOSE 8080
EXPOSE 8081

# Use the official .NET 9.0 SDK image for building
FROM mcr.microsoft.com/dotnet/sdk:9.0 AS build
WORKDIR /src

# Copy the project file and restore dependencies
COPY api.fsproj .
RUN dotnet restore

# Copy the entire source code
COPY . .

# Build the application
RUN dotnet build "api.fsproj" -c Release -o /app/build

# Publish the application
FROM build AS publish
RUN dotnet publish "api.fsproj" -c Release -o /app/publish /p:UseAppHost=false

# Final stage - copy the published application to the runtime image
FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "api.dll"]