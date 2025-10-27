# Kotlin Multiplatform App - Development Phases

A Kotlin Multiplatform application built with Decompose, Orbit MVI, and Koala Plot.

## Tech Stack

- **Decompose**: Navigation and routing
- **Orbit MVI**: State management
- **Koala Plot**: Data visualization

## Development Phases

### Phase 1: Routing Setup

**Goal**: Establish basic navigation structure with Decompose

**Tasks**:
- Set up project dependencies for Decompose
- Create root component with navigation stack
- Implement two routes:
  - Route 1: Home/Dashboard screen
  - Route 2: Data visualization screen
- Configure basic navigation between routes

**Deliverables**:
- Working navigation between two screens
- Basic UI scaffolding for each route

---

### Phase 2: Data Layer with Orbit MVI

**Goal**: Implement data fetching and state management

**Tasks**:
- Add Orbit MVI dependencies
- Create mocked API endpoint for data generation
- Define data models for 4-quadrant scatter plot data (x, y coordinates, labels)
- Implement Orbit MVI container:
  - State definition
  - Side effects for data fetching
  - Reducers for state updates
- Wire up data fetching to UI component
- Display loading/success/error states

**Deliverables**:
- Functional mocked data endpoint
- Orbit MVI state container managing data
- UI displaying data loading states

---

### Phase 3: Data Visualization with Koala Plot

**Goal**: Display data in a 4-quadrant scatter plot

**Tasks**:
- Add Koala Plot dependencies
- Create scatter plot component
- Configure 4-quadrant layout:
  - Set up axes with center point at (0, 0)
  - Add quadrant dividing lines
  - Style quadrants (optional: different background colors/labels)
- Plot data points from Orbit MVI state
- Add interactivity (tooltips, point selection, etc.)
- Polish visualization styling

**Deliverables**:
- Fully functional 4-quadrant scatter plot
- Data from Phase 2 displayed visually
- Interactive and responsive chart

---

## Getting Started

```bash
# Clone and build
./gradlew build

# Run Android
./gradlew :androidApp:installDebug

# Run iOS
cd iosApp && xcodebuild
```

## Project Structure

```
├── shared/
│   ├── src/commonMain/
│   │   ├── components/      # Decompose components
│   │   ├── viewmodels/      # Orbit MVI containers
│   │   ├── ui/              # Compose UI
│   │   └── data/            # Data models and API
│   ├── src/androidMain/
│   └── src/iosMain/
├── androidApp/
└── iosApp/
```

## Dependencies

```kotlin
// Decompose
implementation("com.arkivanov.decompose:decompose:x.x.x")
implementation("com.arkivanov.decompose:extensions-compose:x.x.x")

// Orbit MVI
implementation("org.orbit-mvi:orbit-core:x.x.x")
implementation("org.orbit-mvi:orbit-compose:x.x.x")

// Koala Plot
implementation("io.github.koalaplot:koalaplot-core:x.x.x")
```
