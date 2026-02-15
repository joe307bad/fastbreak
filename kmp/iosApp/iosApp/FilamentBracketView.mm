#import "FilamentBracketView.h"
#import <QuartzCore/CAMetalLayer.h>

#include <filament/Engine.h>
#include <filament/Renderer.h>
#include <filament/Scene.h>
#include <filament/View.h>
#include <filament/Camera.h>
#include <filament/Skybox.h>
#include <filament/SwapChain.h>
#include <filament/Viewport.h>
#include <filament/VertexBuffer.h>
#include <filament/IndexBuffer.h>
#include <filament/Material.h>
#include <filament/MaterialInstance.h>
#include <filament/RenderableManager.h>
#include <filament/TransformManager.h>
#include <filament/Box.h>
#include <utils/EntityManager.h>
#include <math/vec3.h>
#include <math/vec4.h>

#include <vector>
#include <cmath>

using namespace filament;
using namespace utils;
using namespace filament::math;

// Simple bracket data structures
struct BracketTeamData {
    int seed;
    const char* name;
    int score;
};

struct BracketMatchupData {
    BracketTeamData team1;
    BracketTeamData team2;
    int winner; // 1 or 2
};

struct BracketRoundData {
    const char* name;
    std::vector<BracketMatchupData> matchups;
};

@implementation FilamentBracketUIView {
    Engine* _engine;
    Renderer* _renderer;
    Scene* _scene;
    filament::View* _filamentView;
    Camera* _camera;
    SwapChain* _swapChain;
    Skybox* _skybox;
    Material* _material;

    std::vector<Entity> _renderables;
    std::vector<VertexBuffer*> _vertexBuffers;
    std::vector<IndexBuffer*> _indexBuffers;

    Entity _cameraEntity;
    CADisplayLink* _displayLink;
    BOOL _isSetup;
}

+ (Class)layerClass {
    return [CAMetalLayer class];
}

- (instancetype)initWithFrame:(CGRect)frame {
    self = [super initWithFrame:frame];
    if (self) {
        _isSetup = NO;
        self.contentScaleFactor = [UIScreen mainScreen].scale;
    }
    return self;
}

- (void)didMoveToWindow {
    [super didMoveToWindow];
    if (self.window && !_isSetup) {
        [self setupFilament];
        [self startRendering];
    } else if (!self.window) {
        [self stopRendering];
    }
}

- (void)layoutSubviews {
    [super layoutSubviews];
    if (!_isSetup) return;

    CGFloat scale = self.contentScaleFactor;
    CGSize size = self.bounds.size;
    CAMetalLayer* metalLayer = (CAMetalLayer*)self.layer;
    metalLayer.drawableSize = CGSizeMake(size.width * scale, size.height * scale);

    uint32_t w = (uint32_t)(size.width * scale);
    uint32_t h = (uint32_t)(size.height * scale);
    if (w > 0 && h > 0) {
        _filamentView->setViewport({0, 0, w, h});
        double aspect = (double)w / (double)h;
        _camera->setProjection(45.0, aspect, 0.1, 100.0);
    }
}

- (void)setupFilament {
    // Configure Metal layer
    CAMetalLayer* metalLayer = (CAMetalLayer*)self.layer;
    metalLayer.pixelFormat = MTLPixelFormatBGRA8Unorm;

    // Create engine with Metal backend
    _engine = Engine::create(Engine::Backend::METAL);
    _renderer = _engine->createRenderer();
    _scene = _engine->createScene();
    _filamentView = _engine->createView();

    // Create camera
    _cameraEntity = EntityManager::get().create();
    _camera = _engine->createCamera(_cameraEntity);

    // Create swap chain from Metal layer
    _swapChain = _engine->createSwapChain((__bridge void*)metalLayer);

    // Configure view
    _filamentView->setCamera(_camera);
    _filamentView->setScene(_scene);

    // Dark blue-gray background
    _skybox = Skybox::Builder()
        .color({0.1f, 0.1f, 0.15f, 1.0f})
        .build(*_engine);
    _scene->setSkybox(_skybox);

    // Position camera
    _camera->lookAt(
        float3{0.0f, 0.5f, 6.0f},
        float3{0.0f, 0.0f, 0.0f},
        float3{0.0f, 1.0f, 0.0f}
    );

    // Set initial viewport
    CGFloat scale = self.contentScaleFactor;
    uint32_t w = (uint32_t)(self.bounds.size.width * scale);
    uint32_t h = (uint32_t)(self.bounds.size.height * scale);
    if (w > 0 && h > 0) {
        _filamentView->setViewport({0, 0, w, h});
        double aspect = (double)w / (double)h;
        _camera->setProjection(45.0, aspect, 0.1, 100.0);
    }

    // Load material
    [self loadMaterial];

    // Create bracket geometry
    [self createBracketGeometry];

    _isSetup = YES;
}

- (void)loadMaterial {
    NSString* path = [[NSBundle mainBundle] pathForResource:@"bracket_material" ofType:@"filamat"];
    if (!path) {
        NSLog(@"ERROR: Could not find bracket_material.filamat in bundle");
        return;
    }
    NSData* data = [NSData dataWithContentsOfFile:path];
    _material = Material::Builder()
        .package(data.bytes, data.length)
        .build(*_engine);
}

- (void)createBracketGeometry {
    if (!_material) return;

    // Fake bracket data: 8-team single elimination
    std::vector<BracketRoundData> rounds;

    // Round of 64
    BracketRoundData round1;
    round1.name = "Round of 64";
    round1.matchups = {
        {{1, "Duke", 82}, {16, "Norfolk St", 55}, 1},
        {{8, "Wisconsin", 64}, {9, "Memphis", 67}, 2},
        {{4, "Auburn", 78}, {13, "Vermont", 61}, 1},
        {{5, "Gonzaga", 70}, {12, "McNeese", 72}, 2}
    };
    rounds.push_back(round1);

    // Round of 32
    BracketRoundData round2;
    round2.name = "Round of 32";
    round2.matchups = {
        {{1, "Duke", 75}, {9, "Memphis", 68}, 1},
        {{4, "Auburn", 80}, {12, "McNeese", 65}, 1}
    };
    rounds.push_back(round2);

    // Sweet 16
    BracketRoundData round3;
    round3.name = "Sweet 16";
    round3.matchups = {
        {{1, "Duke", 71}, {4, "Auburn", 69}, 1}
    };
    rounds.push_back(round3);

    float cardWidth = 1.4f;
    float cardHeight = 0.35f;
    float roundSpacingX = 2.0f;
    float matchupSpacingY = 1.0f;
    float roundDepthZ = 0.8f;

    // Colors per round (RGB)
    float roundColors[][3] = {
        {0.30f, 0.69f, 0.31f},  // Green
        {0.13f, 0.59f, 0.95f},  // Blue
        {1.00f, 0.60f, 0.00f}   // Orange
    };

    int numRounds = (int)rounds.size();

    for (int roundIndex = 0; roundIndex < numRounds; roundIndex++) {
        auto& round = rounds[roundIndex];
        int matchupCount = (int)round.matchups.size();
        float totalHeight = (matchupCount - 1) * matchupSpacingY;

        for (int matchupIndex = 0; matchupIndex < matchupCount; matchupIndex++) {
            float x = (roundIndex - (numRounds - 1) / 2.0f) * roundSpacingX;
            float y = matchupIndex * matchupSpacingY - totalHeight / 2.0f;
            float z = -roundIndex * roundDepthZ;

            float r = roundColors[roundIndex][0];
            float g = roundColors[roundIndex][1];
            float b = roundColors[roundIndex][2];

            [self createQuadAtX:x y:y z:z
                          width:cardWidth height:cardHeight
                              r:r g:g b:b];
        }
    }

    // Connecting lines between rounds
    for (int roundIndex = 0; roundIndex < numRounds - 1; roundIndex++) {
        auto& currentRound = rounds[roundIndex];
        auto& nextRound = rounds[roundIndex + 1];
        int currentCount = (int)currentRound.matchups.size();
        int nextCount = (int)nextRound.matchups.size();
        float currentTotal = (currentCount - 1) * matchupSpacingY;
        float nextTotal = (nextCount - 1) * matchupSpacingY;

        for (int nextIdx = 0; nextIdx < nextCount; nextIdx++) {
            int src1 = nextIdx * 2;
            int src2 = nextIdx * 2 + 1;

            float toX = ((roundIndex + 1) - (numRounds - 1) / 2.0f) * roundSpacingX - cardWidth / 2.0f;
            float toY = nextIdx * matchupSpacingY - nextTotal / 2.0f;
            float toZ = -(roundIndex + 1) * roundDepthZ;

            if (src1 < currentCount) {
                float fromX = (roundIndex - (numRounds - 1) / 2.0f) * roundSpacingX + cardWidth / 2.0f;
                float fromY = src1 * matchupSpacingY - currentTotal / 2.0f;
                float fromZ = -roundIndex * roundDepthZ;
                [self createLineFromX:fromX y:fromY z:fromZ
                                  toX:toX toY:toY toZ:toZ
                                    r:0.8f g:0.8f b:0.8f];
            }
            if (src2 < currentCount) {
                float fromX = (roundIndex - (numRounds - 1) / 2.0f) * roundSpacingX + cardWidth / 2.0f;
                float fromY = src2 * matchupSpacingY - currentTotal / 2.0f;
                float fromZ = -roundIndex * roundDepthZ;
                [self createLineFromX:fromX y:fromY z:fromZ
                                  toX:toX toY:toY toZ:toZ
                                    r:0.8f g:0.8f b:0.8f];
            }
        }
    }
}

- (void)createQuadAtX:(float)cx y:(float)cy z:(float)cz
                width:(float)w height:(float)h
                    r:(float)r g:(float)g b:(float)b {
    float hw = w / 2.0f;
    float hh = h / 2.0f;

    uint8_t cr = (uint8_t)(r * 255);
    uint8_t cg = (uint8_t)(g * 255);
    uint8_t cb = (uint8_t)(b * 255);
    uint8_t ca = 255;

    // Interleaved vertex data: float3 position + ubyte4 color
    struct Vertex {
        float3 position;
        uint8_t color[4];
    };

    Vertex vertices[4] = {
        {{cx - hw, cy - hh, cz}, {cr, cg, cb, ca}},
        {{cx + hw, cy - hh, cz}, {cr, cg, cb, ca}},
        {{cx + hw, cy + hh, cz}, {cr, cg, cb, ca}},
        {{cx - hw, cy + hh, cz}, {cr, cg, cb, ca}},
    };

    uint16_t indices[6] = {0, 1, 2, 0, 2, 3};

    auto vb = VertexBuffer::Builder()
        .bufferCount(1)
        .vertexCount(4)
        .attribute(VertexAttribute::POSITION, 0, VertexBuffer::AttributeType::FLOAT3, 0, sizeof(Vertex))
        .attribute(VertexAttribute::COLOR, 0, VertexBuffer::AttributeType::UBYTE4, sizeof(float3), sizeof(Vertex))
        .normalized(VertexAttribute::COLOR)
        .build(*_engine);

    vb->setBufferAt(*_engine, 0, VertexBuffer::BufferDescriptor(
        vertices, sizeof(vertices), nullptr));
    _vertexBuffers.push_back(vb);

    auto ib = IndexBuffer::Builder()
        .indexCount(6)
        .bufferType(IndexBuffer::Builder::IndexType::USHORT)
        .build(*_engine);

    ib->setBuffer(*_engine, IndexBuffer::BufferDescriptor(
        indices, sizeof(indices), nullptr));
    _indexBuffers.push_back(ib);

    Entity entity = EntityManager::get().create();

    RenderableManager::Builder(1)
        .boundingBox({{cx - hw, cy - hh, cz - 0.01f}, {cx + hw, cy + hh, cz + 0.01f}})
        .geometry(0, RenderableManager::PrimitiveType::TRIANGLES, vb, ib, 0, 6)
        .material(0, _material->getDefaultInstance())
        .build(*_engine, entity);

    _scene->addEntity(entity);
    _renderables.push_back(entity);
}

- (void)createLineFromX:(float)x1 y:(float)y1 z:(float)z1
                    toX:(float)x2 toY:(float)y2 toZ:(float)z2
                      r:(float)r g:(float)g b:(float)b {
    float thickness = 0.02f;

    uint8_t cr = (uint8_t)(r * 255);
    uint8_t cg = (uint8_t)(g * 255);
    uint8_t cb = (uint8_t)(b * 255);
    uint8_t ca = 255;

    struct Vertex {
        float3 position;
        uint8_t color[4];
    };

    Vertex vertices[4] = {
        {{x1, y1 - thickness, z1}, {cr, cg, cb, ca}},
        {{x2, y2 - thickness, z2}, {cr, cg, cb, ca}},
        {{x2, y2 + thickness, z2}, {cr, cg, cb, ca}},
        {{x1, y1 + thickness, z1}, {cr, cg, cb, ca}},
    };

    uint16_t indices[6] = {0, 1, 2, 0, 2, 3};

    auto vb = VertexBuffer::Builder()
        .bufferCount(1)
        .vertexCount(4)
        .attribute(VertexAttribute::POSITION, 0, VertexBuffer::AttributeType::FLOAT3, 0, sizeof(Vertex))
        .attribute(VertexAttribute::COLOR, 0, VertexBuffer::AttributeType::UBYTE4, sizeof(float3), sizeof(Vertex))
        .normalized(VertexAttribute::COLOR)
        .build(*_engine);

    vb->setBufferAt(*_engine, 0, VertexBuffer::BufferDescriptor(
        vertices, sizeof(vertices), nullptr));
    _vertexBuffers.push_back(vb);

    auto ib = IndexBuffer::Builder()
        .indexCount(6)
        .bufferType(IndexBuffer::Builder::IndexType::USHORT)
        .build(*_engine);

    ib->setBuffer(*_engine, IndexBuffer::BufferDescriptor(
        indices, sizeof(indices), nullptr));
    _indexBuffers.push_back(ib);

    Entity entity = EntityManager::get().create();

    float minX = fmin(x1, x2), maxX = fmax(x1, x2);
    float minY = fmin(y1, y2) - thickness, maxY = fmax(y1, y2) + thickness;
    float minZ = fmin(z1, z2), maxZ = fmax(z1, z2);

    RenderableManager::Builder(1)
        .boundingBox({{minX, minY, minZ - 0.01f}, {maxX, maxY, maxZ + 0.01f}})
        .geometry(0, RenderableManager::PrimitiveType::TRIANGLES, vb, ib, 0, 6)
        .material(0, _material->getDefaultInstance())
        .build(*_engine, entity);

    _scene->addEntity(entity);
    _renderables.push_back(entity);
}

#pragma mark - Render Loop

- (void)startRendering {
    if (_displayLink) return;
    _displayLink = [CADisplayLink displayLinkWithTarget:self selector:@selector(render:)];
    [_displayLink addToRunLoop:[NSRunLoop mainRunLoop] forMode:NSDefaultRunLoopMode];
}

- (void)stopRendering {
    [_displayLink invalidate];
    _displayLink = nil;
}

- (void)render:(CADisplayLink*)displayLink {
    if (!_isSetup || !_swapChain) return;

    if (_renderer->beginFrame(_swapChain)) {
        _renderer->render(_filamentView);
        _renderer->endFrame();
    }
}

#pragma mark - Cleanup

- (void)removeFromSuperview {
    [self cleanup];
    [super removeFromSuperview];
}

- (void)dealloc {
    [self cleanup];
}

- (void)cleanup {
    [self stopRendering];

    if (!_engine) return;

    for (auto entity : _renderables) {
        _engine->destroy(entity);
        EntityManager::get().destroy(entity);
    }
    _renderables.clear();

    for (auto vb : _vertexBuffers) {
        _engine->destroy(vb);
    }
    _vertexBuffers.clear();

    for (auto ib : _indexBuffers) {
        _engine->destroy(ib);
    }
    _indexBuffers.clear();

    if (_material) _engine->destroy(_material);
    if (_skybox) _engine->destroy(_skybox);
    if (_renderer) _engine->destroy(_renderer);
    if (_filamentView) _engine->destroy(_filamentView);
    if (_scene) _engine->destroy(_scene);
    if (_camera) {
        _engine->destroyCameraComponent(_cameraEntity);
        EntityManager::get().destroy(_cameraEntity);
    }
    if (_swapChain) _engine->destroy(_swapChain);

    Engine::destroy(&_engine);
    _engine = nullptr;
}

@end
