module Fastbreak.Daily.TeamSiteList

// Dictionary of all professional teams and their ESPN site URLs
// Used to provide exact URLs to the LLM for text segmentation

let nbaTeams = Map.ofList [
    // Eastern Conference - Atlantic
    ("Boston Celtics", "https://www.espn.com/nba/team/_/name/bos/boston-celtics")
    ("Brooklyn Nets", "https://www.espn.com/nba/team/_/name/bkn/brooklyn-nets")
    ("New York Knicks", "https://www.espn.com/nba/team/_/name/ny/new-york-knicks")
    ("Philadelphia 76ers", "https://www.espn.com/nba/team/_/name/phi/philadelphia-76ers")
    ("Toronto Raptors", "https://www.espn.com/nba/team/_/name/tor/toronto-raptors")
    // Eastern Conference - Central
    ("Chicago Bulls", "https://www.espn.com/nba/team/_/name/chi/chicago-bulls")
    ("Cleveland Cavaliers", "https://www.espn.com/nba/team/_/name/cle/cleveland-cavaliers")
    ("Detroit Pistons", "https://www.espn.com/nba/team/_/name/det/detroit-pistons")
    ("Indiana Pacers", "https://www.espn.com/nba/team/_/name/ind/indiana-pacers")
    ("Milwaukee Bucks", "https://www.espn.com/nba/team/_/name/mil/milwaukee-bucks")
    // Eastern Conference - Southeast
    ("Atlanta Hawks", "https://www.espn.com/nba/team/_/name/atl/atlanta-hawks")
    ("Charlotte Hornets", "https://www.espn.com/nba/team/_/name/cha/charlotte-hornets")
    ("Miami Heat", "https://www.espn.com/nba/team/_/name/mia/miami-heat")
    ("Orlando Magic", "https://www.espn.com/nba/team/_/name/orl/orlando-magic")
    ("Washington Wizards", "https://www.espn.com/nba/team/_/name/wsh/washington-wizards")
    // Western Conference - Northwest
    ("Denver Nuggets", "https://www.espn.com/nba/team/_/name/den/denver-nuggets")
    ("Minnesota Timberwolves", "https://www.espn.com/nba/team/_/name/min/minnesota-timberwolves")
    ("Oklahoma City Thunder", "https://www.espn.com/nba/team/_/name/okc/oklahoma-city-thunder")
    ("Portland Trail Blazers", "https://www.espn.com/nba/team/_/name/por/portland-trail-blazers")
    ("Utah Jazz", "https://www.espn.com/nba/team/_/name/utah/utah-jazz")
    // Western Conference - Pacific
    ("Golden State Warriors", "https://www.espn.com/nba/team/_/name/gs/golden-state-warriors")
    ("Los Angeles Clippers", "https://www.espn.com/nba/team/_/name/lac/la-clippers")
    ("Los Angeles Lakers", "https://www.espn.com/nba/team/_/name/lal/los-angeles-lakers")
    ("Phoenix Suns", "https://www.espn.com/nba/team/_/name/phx/phoenix-suns")
    ("Sacramento Kings", "https://www.espn.com/nba/team/_/name/sac/sacramento-kings")
    // Western Conference - Southwest
    ("Dallas Mavericks", "https://www.espn.com/nba/team/_/name/dal/dallas-mavericks")
    ("Houston Rockets", "https://www.espn.com/nba/team/_/name/hou/houston-rockets")
    ("Memphis Grizzlies", "https://www.espn.com/nba/team/_/name/mem/memphis-grizzlies")
    ("New Orleans Pelicans", "https://www.espn.com/nba/team/_/name/no/new-orleans-pelicans")
    ("San Antonio Spurs", "https://www.espn.com/nba/team/_/name/sa/san-antonio-spurs")
]

let nflTeams = Map.ofList [
    // AFC East
    ("Buffalo Bills", "https://www.espn.com/nfl/team/_/name/buf/buffalo-bills")
    ("Miami Dolphins", "https://www.espn.com/nfl/team/_/name/mia/miami-dolphins")
    ("New England Patriots", "https://www.espn.com/nfl/team/_/name/ne/new-england-patriots")
    ("New York Jets", "https://www.espn.com/nfl/team/_/name/nyj/new-york-jets")
    // AFC North
    ("Baltimore Ravens", "https://www.espn.com/nfl/team/_/name/bal/baltimore-ravens")
    ("Cincinnati Bengals", "https://www.espn.com/nfl/team/_/name/cin/cincinnati-bengals")
    ("Cleveland Browns", "https://www.espn.com/nfl/team/_/name/cle/cleveland-browns")
    ("Pittsburgh Steelers", "https://www.espn.com/nfl/team/_/name/pit/pittsburgh-steelers")
    // AFC South
    ("Houston Texans", "https://www.espn.com/nfl/team/_/name/hou/houston-texans")
    ("Indianapolis Colts", "https://www.espn.com/nfl/team/_/name/ind/indianapolis-colts")
    ("Jacksonville Jaguars", "https://www.espn.com/nfl/team/_/name/jax/jacksonville-jaguars")
    ("Tennessee Titans", "https://www.espn.com/nfl/team/_/name/ten/tennessee-titans")
    // AFC West
    ("Denver Broncos", "https://www.espn.com/nfl/team/_/name/den/denver-broncos")
    ("Kansas City Chiefs", "https://www.espn.com/nfl/team/_/name/kc/kansas-city-chiefs")
    ("Las Vegas Raiders", "https://www.espn.com/nfl/team/_/name/lv/las-vegas-raiders")
    ("Los Angeles Chargers", "https://www.espn.com/nfl/team/_/name/lac/los-angeles-chargers")
    // NFC East
    ("Dallas Cowboys", "https://www.espn.com/nfl/team/_/name/dal/dallas-cowboys")
    ("New York Giants", "https://www.espn.com/nfl/team/_/name/nyg/new-york-giants")
    ("Philadelphia Eagles", "https://www.espn.com/nfl/team/_/name/phi/philadelphia-eagles")
    ("Washington Commanders", "https://www.espn.com/nfl/team/_/name/wsh/washington-commanders")
    // NFC North
    ("Chicago Bears", "https://www.espn.com/nfl/team/_/name/chi/chicago-bears")
    ("Detroit Lions", "https://www.espn.com/nfl/team/_/name/det/detroit-lions")
    ("Green Bay Packers", "https://www.espn.com/nfl/team/_/name/gb/green-bay-packers")
    ("Minnesota Vikings", "https://www.espn.com/nfl/team/_/name/min/minnesota-vikings")
    // NFC South
    ("Atlanta Falcons", "https://www.espn.com/nfl/team/_/name/atl/atlanta-falcons")
    ("Carolina Panthers", "https://www.espn.com/nfl/team/_/name/car/carolina-panthers")
    ("New Orleans Saints", "https://www.espn.com/nfl/team/_/name/no/new-orleans-saints")
    ("Tampa Bay Buccaneers", "https://www.espn.com/nfl/team/_/name/tb/tampa-bay-buccaneers")
    // NFC West
    ("Arizona Cardinals", "https://www.espn.com/nfl/team/_/name/ari/arizona-cardinals")
    ("Los Angeles Rams", "https://www.espn.com/nfl/team/_/name/lar/los-angeles-rams")
    ("San Francisco 49ers", "https://www.espn.com/nfl/team/_/name/sf/san-francisco-49ers")
    ("Seattle Seahawks", "https://www.espn.com/nfl/team/_/name/sea/seattle-seahawks")
]

let nhlTeams = Map.ofList [
    // Atlantic Division
    ("Boston Bruins", "https://www.espn.com/nhl/team/_/name/bos/boston-bruins")
    ("Buffalo Sabres", "https://www.espn.com/nhl/team/_/name/buf/buffalo-sabres")
    ("Detroit Red Wings", "https://www.espn.com/nhl/team/_/name/det/detroit-red-wings")
    ("Florida Panthers", "https://www.espn.com/nhl/team/_/name/fla/florida-panthers")
    ("Montreal Canadiens", "https://www.espn.com/nhl/team/_/name/mtl/montreal-canadiens")
    ("Ottawa Senators", "https://www.espn.com/nhl/team/_/name/ott/ottawa-senators")
    ("Tampa Bay Lightning", "https://www.espn.com/nhl/team/_/name/tb/tampa-bay-lightning")
    ("Toronto Maple Leafs", "https://www.espn.com/nhl/team/_/name/tor/toronto-maple-leafs")
    // Metropolitan Division
    ("Carolina Hurricanes", "https://www.espn.com/nhl/team/_/name/car/carolina-hurricanes")
    ("Columbus Blue Jackets", "https://www.espn.com/nhl/team/_/name/cbj/columbus-blue-jackets")
    ("New Jersey Devils", "https://www.espn.com/nhl/team/_/name/nj/new-jersey-devils")
    ("New York Islanders", "https://www.espn.com/nhl/team/_/name/nyi/new-york-islanders")
    ("New York Rangers", "https://www.espn.com/nhl/team/_/name/nyr/new-york-rangers")
    ("Philadelphia Flyers", "https://www.espn.com/nhl/team/_/name/phi/philadelphia-flyers")
    ("Pittsburgh Penguins", "https://www.espn.com/nhl/team/_/name/pit/pittsburgh-penguins")
    ("Washington Capitals", "https://www.espn.com/nhl/team/_/name/wsh/washington-capitals")
    // Central Division
    ("Chicago Blackhawks", "https://www.espn.com/nhl/team/_/name/chi/chicago-blackhawks")
    ("Colorado Avalanche", "https://www.espn.com/nhl/team/_/name/col/colorado-avalanche")
    ("Dallas Stars", "https://www.espn.com/nhl/team/_/name/dal/dallas-stars")
    ("Minnesota Wild", "https://www.espn.com/nhl/team/_/name/min/minnesota-wild")
    ("Nashville Predators", "https://www.espn.com/nhl/team/_/name/nsh/nashville-predators")
    ("St. Louis Blues", "https://www.espn.com/nhl/team/_/name/stl/st-louis-blues")
    ("Winnipeg Jets", "https://www.espn.com/nhl/team/_/name/wpg/winnipeg-jets")
    // Pacific Division
    ("Anaheim Ducks", "https://www.espn.com/nhl/team/_/name/ana/anaheim-ducks")
    ("Calgary Flames", "https://www.espn.com/nhl/team/_/name/cgy/calgary-flames")
    ("Edmonton Oilers", "https://www.espn.com/nhl/team/_/name/edm/edmonton-oilers")
    ("Los Angeles Kings", "https://www.espn.com/nhl/team/_/name/la/los-angeles-kings")
    ("San Jose Sharks", "https://www.espn.com/nhl/team/_/name/sj/san-jose-sharks")
    ("Seattle Kraken", "https://www.espn.com/nhl/team/_/name/sea/seattle-kraken")
    ("Vancouver Canucks", "https://www.espn.com/nhl/team/_/name/van/vancouver-canucks")
    ("Vegas Golden Knights", "https://www.espn.com/nhl/team/_/name/vgk/vegas-golden-knights")
    ("Utah Hockey Club", "https://www.espn.com/nhl/team/_/name/utah/utah-hockey-club")
]

let cbbTeams = Map.ofList [
    // ACC
    ("Duke Blue Devils", "https://www.espn.com/mens-college-basketball/team/_/id/150/duke-blue-devils")
    ("North Carolina Tar Heels", "https://www.espn.com/mens-college-basketball/team/_/id/153/north-carolina-tar-heels")
    ("Virginia Cavaliers", "https://www.espn.com/mens-college-basketball/team/_/id/258/virginia-cavaliers")
    ("Louisville Cardinals", "https://www.espn.com/mens-college-basketball/team/_/id/97/louisville-cardinals")
    ("Syracuse Orange", "https://www.espn.com/mens-college-basketball/team/_/id/183/syracuse-orange")
    ("Florida State Seminoles", "https://www.espn.com/mens-college-basketball/team/_/id/52/florida-state-seminoles")
    ("Miami Hurricanes", "https://www.espn.com/mens-college-basketball/team/_/id/2390/miami-hurricanes")
    ("NC State Wolfpack", "https://www.espn.com/mens-college-basketball/team/_/id/152/nc-state-wolfpack")
    ("Clemson Tigers", "https://www.espn.com/mens-college-basketball/team/_/id/228/clemson-tigers")
    ("Wake Forest Demon Deacons", "https://www.espn.com/mens-college-basketball/team/_/id/154/wake-forest-demon-deacons")
    ("Pittsburgh Panthers", "https://www.espn.com/mens-college-basketball/team/_/id/221/pittsburgh-panthers")
    ("Notre Dame Fighting Irish", "https://www.espn.com/mens-college-basketball/team/_/id/87/notre-dame-fighting-irish")
    ("Georgia Tech Yellow Jackets", "https://www.espn.com/mens-college-basketball/team/_/id/59/georgia-tech-yellow-jackets")
    ("Virginia Tech Hokies", "https://www.espn.com/mens-college-basketball/team/_/id/259/virginia-tech-hokies")
    ("Boston College Eagles", "https://www.espn.com/mens-college-basketball/team/_/id/103/boston-college-eagles")
    ("California Golden Bears", "https://www.espn.com/mens-college-basketball/team/_/id/25/california-golden-bears")
    ("Stanford Cardinal", "https://www.espn.com/mens-college-basketball/team/_/id/24/stanford-cardinal")
    ("SMU Mustangs", "https://www.espn.com/mens-college-basketball/team/_/id/2567/smu-mustangs")
    // Big Ten
    ("Michigan State Spartans", "https://www.espn.com/mens-college-basketball/team/_/id/127/michigan-state-spartans")
    ("Michigan Wolverines", "https://www.espn.com/mens-college-basketball/team/_/id/130/michigan-wolverines")
    ("Ohio State Buckeyes", "https://www.espn.com/mens-college-basketball/team/_/id/194/ohio-state-buckeyes")
    ("Indiana Hoosiers", "https://www.espn.com/mens-college-basketball/team/_/id/84/indiana-hoosiers")
    ("Purdue Boilermakers", "https://www.espn.com/mens-college-basketball/team/_/id/2509/purdue-boilermakers")
    ("Illinois Fighting Illini", "https://www.espn.com/mens-college-basketball/team/_/id/356/illinois-fighting-illini")
    ("Wisconsin Badgers", "https://www.espn.com/mens-college-basketball/team/_/id/275/wisconsin-badgers")
    ("Iowa Hawkeyes", "https://www.espn.com/mens-college-basketball/team/_/id/2294/iowa-hawkeyes")
    ("Maryland Terrapins", "https://www.espn.com/mens-college-basketball/team/_/id/120/maryland-terrapins")
    ("Minnesota Golden Gophers", "https://www.espn.com/mens-college-basketball/team/_/id/135/minnesota-golden-gophers")
    ("Penn State Nittany Lions", "https://www.espn.com/mens-college-basketball/team/_/id/213/penn-state-nittany-lions")
    ("Northwestern Wildcats", "https://www.espn.com/mens-college-basketball/team/_/id/77/northwestern-wildcats")
    ("Rutgers Scarlet Knights", "https://www.espn.com/mens-college-basketball/team/_/id/164/rutgers-scarlet-knights")
    ("Nebraska Cornhuskers", "https://www.espn.com/mens-college-basketball/team/_/id/158/nebraska-cornhuskers")
    ("UCLA Bruins", "https://www.espn.com/mens-college-basketball/team/_/id/26/ucla-bruins")
    ("USC Trojans", "https://www.espn.com/mens-college-basketball/team/_/id/30/usc-trojans")
    ("Oregon Ducks", "https://www.espn.com/mens-college-basketball/team/_/id/2483/oregon-ducks")
    ("Washington Huskies", "https://www.espn.com/mens-college-basketball/team/_/id/264/washington-huskies")
    // Big 12
    ("Kansas Jayhawks", "https://www.espn.com/mens-college-basketball/team/_/id/2305/kansas-jayhawks")
    ("Baylor Bears", "https://www.espn.com/mens-college-basketball/team/_/id/239/baylor-bears")
    ("Texas Longhorns", "https://www.espn.com/mens-college-basketball/team/_/id/251/texas-longhorns")
    ("Texas Tech Red Raiders", "https://www.espn.com/mens-college-basketball/team/_/id/2641/texas-tech-red-raiders")
    ("Oklahoma Sooners", "https://www.espn.com/mens-college-basketball/team/_/id/201/oklahoma-sooners")
    ("Oklahoma State Cowboys", "https://www.espn.com/mens-college-basketball/team/_/id/197/oklahoma-state-cowboys")
    ("West Virginia Mountaineers", "https://www.espn.com/mens-college-basketball/team/_/id/277/west-virginia-mountaineers")
    ("TCU Horned Frogs", "https://www.espn.com/mens-college-basketball/team/_/id/2628/tcu-horned-frogs")
    ("Kansas State Wildcats", "https://www.espn.com/mens-college-basketball/team/_/id/2306/kansas-state-wildcats")
    ("Iowa State Cyclones", "https://www.espn.com/mens-college-basketball/team/_/id/66/iowa-state-cyclones")
    ("Houston Cougars", "https://www.espn.com/mens-college-basketball/team/_/id/248/houston-cougars")
    ("Cincinnati Bearcats", "https://www.espn.com/mens-college-basketball/team/_/id/2132/cincinnati-bearcats")
    ("UCF Knights", "https://www.espn.com/mens-college-basketball/team/_/id/2116/ucf-knights")
    ("BYU Cougars", "https://www.espn.com/mens-college-basketball/team/_/id/252/byu-cougars")
    ("Arizona Wildcats", "https://www.espn.com/mens-college-basketball/team/_/id/12/arizona-wildcats")
    ("Arizona State Sun Devils", "https://www.espn.com/mens-college-basketball/team/_/id/9/arizona-state-sun-devils")
    ("Colorado Buffaloes", "https://www.espn.com/mens-college-basketball/team/_/id/38/colorado-buffaloes")
    ("Utah Utes", "https://www.espn.com/mens-college-basketball/team/_/id/254/utah-utes")
    // SEC
    ("Kentucky Wildcats", "https://www.espn.com/mens-college-basketball/team/_/id/96/kentucky-wildcats")
    ("Tennessee Volunteers", "https://www.espn.com/mens-college-basketball/team/_/id/2633/tennessee-volunteers")
    ("Alabama Crimson Tide", "https://www.espn.com/mens-college-basketball/team/_/id/333/alabama-crimson-tide")
    ("Auburn Tigers", "https://www.espn.com/mens-college-basketball/team/_/id/2/auburn-tigers")
    ("Arkansas Razorbacks", "https://www.espn.com/mens-college-basketball/team/_/id/8/arkansas-razorbacks")
    ("Florida Gators", "https://www.espn.com/mens-college-basketball/team/_/id/57/florida-gators")
    ("LSU Tigers", "https://www.espn.com/mens-college-basketball/team/_/id/99/lsu-tigers")
    ("Georgia Bulldogs", "https://www.espn.com/mens-college-basketball/team/_/id/61/georgia-bulldogs")
    ("Ole Miss Rebels", "https://www.espn.com/mens-college-basketball/team/_/id/145/ole-miss-rebels")
    ("Mississippi State Bulldogs", "https://www.espn.com/mens-college-basketball/team/_/id/344/mississippi-state-bulldogs")
    ("Missouri Tigers", "https://www.espn.com/mens-college-basketball/team/_/id/142/missouri-tigers")
    ("South Carolina Gamecocks", "https://www.espn.com/mens-college-basketball/team/_/id/2579/south-carolina-gamecocks")
    ("Vanderbilt Commodores", "https://www.espn.com/mens-college-basketball/team/_/id/238/vanderbilt-commodores")
    ("Texas A&M Aggies", "https://www.espn.com/mens-college-basketball/team/_/id/245/texas-am-aggies")
    // Big East
    ("Connecticut Huskies", "https://www.espn.com/mens-college-basketball/team/_/id/41/connecticut-huskies")
    ("Villanova Wildcats", "https://www.espn.com/mens-college-basketball/team/_/id/222/villanova-wildcats")
    ("Creighton Bluejays", "https://www.espn.com/mens-college-basketball/team/_/id/156/creighton-bluejays")
    ("Marquette Golden Eagles", "https://www.espn.com/mens-college-basketball/team/_/id/269/marquette-golden-eagles")
    ("Xavier Musketeers", "https://www.espn.com/mens-college-basketball/team/_/id/2752/xavier-musketeers")
    ("St. John's Red Storm", "https://www.espn.com/mens-college-basketball/team/_/id/2599/st-johns-red-storm")
    ("Seton Hall Pirates", "https://www.espn.com/mens-college-basketball/team/_/id/2550/seton-hall-pirates")
    ("Providence Friars", "https://www.espn.com/mens-college-basketball/team/_/id/2507/providence-friars")
    ("Butler Bulldogs", "https://www.espn.com/mens-college-basketball/team/_/id/2086/butler-bulldogs")
    ("Georgetown Hoyas", "https://www.espn.com/mens-college-basketball/team/_/id/46/georgetown-hoyas")
    ("DePaul Blue Demons", "https://www.espn.com/mens-college-basketball/team/_/id/305/depaul-blue-demons")
    // West Coast
    ("Gonzaga Bulldogs", "https://www.espn.com/mens-college-basketball/team/_/id/2250/gonzaga-bulldogs")
    ("Saint Mary's Gaels", "https://www.espn.com/mens-college-basketball/team/_/id/2608/saint-marys-gaels")
    // Mountain West
    ("San Diego State Aztecs", "https://www.espn.com/mens-college-basketball/team/_/id/21/san-diego-state-aztecs")
    // American Athletic
    ("Memphis Tigers", "https://www.espn.com/mens-college-basketball/team/_/id/235/memphis-tigers")
]

let mlbTeams = Map.ofList [
    // AL East
    ("Baltimore Orioles", "https://www.espn.com/mlb/team/_/name/bal/baltimore-orioles")
    ("Boston Red Sox", "https://www.espn.com/mlb/team/_/name/bos/boston-red-sox")
    ("New York Yankees", "https://www.espn.com/mlb/team/_/name/nyy/new-york-yankees")
    ("Tampa Bay Rays", "https://www.espn.com/mlb/team/_/name/tb/tampa-bay-rays")
    ("Toronto Blue Jays", "https://www.espn.com/mlb/team/_/name/tor/toronto-blue-jays")
    // AL Central
    ("Chicago White Sox", "https://www.espn.com/mlb/team/_/name/chw/chicago-white-sox")
    ("Cleveland Guardians", "https://www.espn.com/mlb/team/_/name/cle/cleveland-guardians")
    ("Detroit Tigers", "https://www.espn.com/mlb/team/_/name/det/detroit-tigers")
    ("Kansas City Royals", "https://www.espn.com/mlb/team/_/name/kc/kansas-city-royals")
    ("Minnesota Twins", "https://www.espn.com/mlb/team/_/name/min/minnesota-twins")
    // AL West
    ("Houston Astros", "https://www.espn.com/mlb/team/_/name/hou/houston-astros")
    ("Los Angeles Angels", "https://www.espn.com/mlb/team/_/name/laa/los-angeles-angels")
    ("Oakland Athletics", "https://www.espn.com/mlb/team/_/name/ath/athletics-athletics")
    ("Seattle Mariners", "https://www.espn.com/mlb/team/_/name/sea/seattle-mariners")
    ("Texas Rangers", "https://www.espn.com/mlb/team/_/name/tex/texas-rangers")
    // NL East
    ("Atlanta Braves", "https://www.espn.com/mlb/team/_/name/atl/atlanta-braves")
    ("Miami Marlins", "https://www.espn.com/mlb/team/_/name/mia/miami-marlins")
    ("New York Mets", "https://www.espn.com/mlb/team/_/name/nym/new-york-mets")
    ("Philadelphia Phillies", "https://www.espn.com/mlb/team/_/name/phi/philadelphia-phillies")
    ("Washington Nationals", "https://www.espn.com/mlb/team/_/name/wsh/washington-nationals")
    // NL Central
    ("Chicago Cubs", "https://www.espn.com/mlb/team/_/name/chc/chicago-cubs")
    ("Cincinnati Reds", "https://www.espn.com/mlb/team/_/name/cin/cincinnati-reds")
    ("Milwaukee Brewers", "https://www.espn.com/mlb/team/_/name/mil/milwaukee-brewers")
    ("Pittsburgh Pirates", "https://www.espn.com/mlb/team/_/name/pit/pittsburgh-pirates")
    ("St. Louis Cardinals", "https://www.espn.com/mlb/team/_/name/stl/st-louis-cardinals")
    // NL West
    ("Arizona Diamondbacks", "https://www.espn.com/mlb/team/_/name/ari/arizona-diamondbacks")
    ("Colorado Rockies", "https://www.espn.com/mlb/team/_/name/col/colorado-rockies")
    ("Los Angeles Dodgers", "https://www.espn.com/mlb/team/_/name/lad/los-angeles-dodgers")
    ("San Diego Padres", "https://www.espn.com/mlb/team/_/name/sd/san-diego-padres")
    ("San Francisco Giants", "https://www.espn.com/mlb/team/_/name/sf/san-francisco-giants")
]

// Get all teams for a league
let getTeamsForLeague (league: string) =
    match league.ToUpperInvariant() with
    | "NBA" -> nbaTeams
    | "NFL" -> nflTeams
    | "NHL" -> nhlTeams
    | "MLB" -> mlbTeams
    | "CBB" -> cbbTeams
    | _ -> Map.empty

// Format team list for LLM prompt
let getTeamListForPrompt (league: string) =
    let teams = getTeamsForLeague league
    teams
    |> Map.toList
    |> List.map (fun (name, url) -> $"  - \"{name}\" -> {url}")
    |> String.concat "\n"
