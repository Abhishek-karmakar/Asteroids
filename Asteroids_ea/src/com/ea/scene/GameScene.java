package com.ea.scene;

import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import org.andengine.engine.camera.Camera;
import org.andengine.engine.camera.hud.HUD;
import org.andengine.engine.camera.hud.controls.AnalogOnScreenControl;
import org.andengine.engine.camera.hud.controls.AnalogOnScreenControl.IAnalogOnScreenControlListener;
import org.andengine.engine.camera.hud.controls.BaseOnScreenControl;
import org.andengine.engine.handler.IUpdateHandler;
import org.andengine.engine.handler.physics.PhysicsHandler;
import org.andengine.engine.handler.timer.ITimerCallback;
import org.andengine.engine.handler.timer.TimerHandler;
import org.andengine.entity.modifier.MoveModifier;
import org.andengine.entity.scene.IOnSceneTouchListener;
import org.andengine.entity.scene.Scene;
import org.andengine.entity.scene.background.AutoParallaxBackground;
import org.andengine.entity.scene.background.ParallaxBackground.ParallaxEntity;
import org.andengine.entity.sprite.AnimatedSprite;
import org.andengine.entity.sprite.Sprite;
import org.andengine.entity.text.Text;
import org.andengine.entity.text.TextOptions;
import org.andengine.input.touch.TouchEvent;
import org.andengine.opengl.texture.TextureOptions;
import org.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlasTextureRegionFactory;
import org.andengine.opengl.texture.atlas.bitmap.BuildableBitmapTextureAtlas;
import org.andengine.opengl.texture.region.TiledTextureRegion;
import org.andengine.opengl.util.GLState;
import org.andengine.util.HorizontalAlign;
import org.andengine.util.math.MathUtils;
import org.andengine.util.modifier.ease.EaseCubicInOut;
import org.andengine.util.modifier.ease.EaseCubicOut;
import org.andengine.util.modifier.ease.EaseExponentialOut;
import org.andengine.util.modifier.ease.IEaseFunction;

import android.opengl.GLES20;
import android.util.Log;

import com.ea.base.BaseScene;
import com.ea.manager.SceneManager;
import com.ea.manager.SceneManager.SceneType;

public class GameScene extends BaseScene implements IOnSceneTouchListener {
	// ---------------------------------
	// Game related variables
	// ---------------------------------
	private int score = 0;

	private HUD gameHUD;
	private Text scoreText;
	// private PhysicsWorld physicsWorld;
	PhysicsHandler playerPhysicshandler;
	int asteroidsCount = 0;

	float player_rotation_value_x = 0.0f;
	float player_rotation_value_y = 0.0f;
	float rotationInRad = 0.0f;

	// take Bullets and Asteroids as sprites currently and later change them to
	// classes extending from
	// Sprites and AnimatedSprites
	private LinkedList<Sprite> bulletLL;
	private LinkedList<Sprite> asteroidsLL;

	private LinkedList<Sprite> bulletsToBeAdded;
	private LinkedList<Sprite> asteroidsToBeAdded;

	boolean gameOver = false;
	private Text gameOverText;
	private boolean gameOverDisplayed = false;

	// controls for the player controls
	float bullet_origin_x = 0.0f;
	float bullet_origin_y = 0.0f;

	float explosion_x = 0.0f;
	float explosion_y = 0.0f;

	// Spaceship texture and animated sprite.
	BuildableBitmapTextureAtlas mbitmapSpaceshipAtlas;
	TiledTextureRegion mSpaceshipTextureRegion;
	AnimatedSprite spaceship;

	// for parralax
	AutoParallaxBackground autoParallaxBackground;
	Thread t;
	Sprite hyperSpaceButton, redButton, blueButton;

	int spaceShipVelocity = 150;
	boolean applyBrakes = false;
	int count = 0;

	@Override
	public void createScene() 
	{
		// create the background
		createBackground();
		createHUD();
		// initialize the spaceship
		initSpaceship();
		// Initialize the Containers for bullets and Asteroids
		initOtherEntities();

		registerPhysicsWithPlayer();

		initOnScreenControls();

		createGameOverText();

		// levelCompleteWindow = new LevelCompleteWindow(vbom);
		setOnSceneTouchListener(this);

		// Method for adding targets in the Game every one Second
		createSpriteSpawnTimeHandler();

		// the update handler to detect collision and to keep the bullet origin
		// fixed to the Spaceship tip
		this.registerUpdateHandler(new IUpdateHandler() 
		{
			public void reset() {
				// TODO Auto-generated method stub
			}

			public void onUpdate(float pSecondsElapsed) 
			{
				// make sure that the bullets are always coming out of the tip
				// of the spaceship.
				// since the location of the spaceship changes. onUpdate updates
				// the location at all times.
				final float[] bulletStartCoordinates = spaceship
						.convertLocalToSceneCoordinates(25, 0);
				bullet_origin_x = bulletStartCoordinates[AnimatedSprite.VERTEX_INDEX_X];
				bullet_origin_y = bulletStartCoordinates[AnimatedSprite.VERTEX_INDEX_Y];

				// check collision detection. Two sprites 1.> Bullet 2.>
				// Asteroids
				Iterator<Sprite> asteroids = asteroidsLL.iterator();
				Sprite _asteroid;
				boolean hit = false;
				while (asteroids.hasNext()) {
					_asteroid = asteroids.next();
					if (_asteroid.collidesWith(spaceship)) 
					{
						startBlastAnimation(spaceship.getX(), spaceship.getY());
						// remove the bullet and the sprite from the Iterators
						detachChild(spaceship);
						removeSprite(_asteroid, asteroids);
						spaceship.setPosition(-50,-50);
						resourcesManager.explosionSound.play();
						
						if (!gameOverDisplayed) 
						{
							displayGameOverText();
						}
						gameOver = true;
					}

					Iterator<Sprite> bullets = bulletLL.iterator();
					Sprite _bullet;
					while (bullets.hasNext()) {
						_bullet = bullets.next();
						if (_asteroid.collidesWith(_bullet)) 
						{
							// show an animation of the burts
							startBlastAnimation(_asteroid.getX(),_asteroid.getY());

							// remove the bullet and the sprite from the
							// Iterators

							removeSprite(_bullet, bullets);
							removeSprite(_asteroid, asteroids);

							hit = true;
							addToScore(1);
						}

					}

				}
				bulletLL.addAll(bulletsToBeAdded);
				bulletsToBeAdded.clear();

				asteroidsLL.addAll(asteroidsToBeAdded);
				asteroidsToBeAdded.clear();
			}

			// method to handel the animation after the bullet hits the asteroid
			private void startBlastAnimation(float _x, float _y) {
				final AnimatedSprite explosion = new AnimatedSprite(_x, _y,
						resourcesManager.explosionReigon, resourcesManager.vbom);
				System.out.println("explosion happening..... ");
				explosion.animate(100);

				attachChild(explosion);
				explosion.registerUpdateHandler(new IUpdateHandler() 
				{
					public void onUpdate(float pSecondsElapsed) 
					{
						if (explosion.getCurrentTileIndex() > 15) 
						{
							explosion.stopAnimation();
							// the following line places the explosion animation
							// out of the screen
							// temporarily solving the problem of watcing it on
							explosion.setPosition(-100, -100);
							// detaching the explosion in the same tread gives
							// an error
							// To solve it put it in he update thread of the
							// Gameactivity.
							// detachChild(explosion);
							// explosion.unregisterUpdateHandler(this);
							resourcesManager.activity.runOnUpdateThread(new Runnable() 
							{
								public void run() 
								{
									detachChild(explosion);
								}
							});
						}
					}

					public void reset() 
					{
					}
				});

			}

			// Also safely remove sprite
			/*
			 * safely detach the sprite from the scene and remove it from the
			 * iterator
			 */
			public void removeSprite(final Sprite _sprite, Iterator<Sprite> it) 
			{
				resourcesManager.activity.runOnUpdateThread(new Runnable() 
				{
					public void run() 
					{
						detachChild(_sprite);
					}
				});
				it.remove();
				it = null;
			}
		});
		
		// starting background music
		resourcesManager.backgroundMusic.play();
		System.out.println("finishing this .............");
	}

	private void initSpaceship() 
	{
		// BitmapTextureAtlasTextureRegionFactory.setAssetBasePath("gfx/");
		mbitmapSpaceshipAtlas = new BuildableBitmapTextureAtlas(
				resourcesManager.activity.getTextureManager(), 1024, 1024,
				TextureOptions.NEAREST);
		mSpaceshipTextureRegion = BitmapTextureAtlasTextureRegionFactory
				.createTiledFromAsset(mbitmapSpaceshipAtlas,
						resourcesManager.activity, "player.png", 3, 1);
		mbitmapSpaceshipAtlas.load();

		int x = (int) (camera.getWidth() / 2);
		int y = (int) (camera.getHeight() / 2);

		// spaceship = new AnimatedSprite(x, y, mSpaceshipTextureRegion, vbom);
		spaceship = new AnimatedSprite(x, y, resourcesManager.player_region,
				resourcesManager.vbom) {
			// the code for shooting the bullets from the tip of the ship hve
			// been moved from here to Update handler
			// otherwise the animation stops
			@Override
			protected void onManagedUpdate(final float pSecondsElapsed) {
				super.onManagedUpdate(pSecondsElapsed);
			}

			@Override
			protected void onManagedDraw(GLState pGLState, Camera pCamera) {
				super.onManagedDraw(pGLState, pCamera);
			}
		};

		// spaceship.setScale(1.5f); // to make it look bigger ..
		attachChild(spaceship); // add the spaceship to the screen
	}

	public void registerPhysicsWithPlayer() {
		playerPhysicshandler = new PhysicsHandler(spaceship);
		spaceship.registerUpdateHandler(playerPhysicshandler);
	}

	// a Time Handler for spawning targets, triggers every 1 second
	private void createSpriteSpawnTimeHandler() {
		TimerHandler spriteTimerHandler;
		float mEffectSpawnDelay = 1f;
		spriteTimerHandler = new TimerHandler(mEffectSpawnDelay, true,
				new ITimerCallback() {
			public void onTimePassed(TimerHandler pTimerHandler) {
				asteroidsCount++;
				addTarget(asteroidsCount);
			}
		});

		resourcesManager.activity.getEngine().registerUpdateHandler(
				spriteTimerHandler);
	}

	// create the onscreen controls
	private void initOnScreenControls() {
		/* rotation control (left). */
		final float x1 = 0;
		final float y1 = camera.getHeight()
				- this.resourcesManager.mOnScreenControlBaseTextureRegion
				.getHeight();

		AnalogOnScreenControl analogOnScreenControl = new AnalogOnScreenControl(
				x1, y1, camera,
				resourcesManager.mOnScreenControlBaseTextureRegion,
				resourcesManager.mOnScreenControlKnobTextureRegion, 0.1f,
				resourcesManager.activity.getVertexBufferObjectManager(),
				new IAnalogOnScreenControlListener() {

					public void onControlChange(
							BaseOnScreenControl pBaseOnScreenControl,
							float pValueX, float pValueY) {
						// keeping the value
						player_rotation_value_x = pValueX;
						player_rotation_value_y = pValueY;
						rotationInRad = (float) Math.atan2(pValueX, -pValueY);
						spaceship.setRotation(MathUtils.radToDeg(rotationInRad));
					}

					public void onControlClick(
							AnalogOnScreenControl pAnalogOnScreenControl) {

					}
				});

		analogOnScreenControl.getControlBase().setBlendFunction(
				GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
		analogOnScreenControl.getControlBase().setAlpha(0.5f);
		analogOnScreenControl.refreshControlKnobPosition();

		setChildScene(analogOnScreenControl);

		this.setTouchAreaBindingOnActionDownEnabled(true);

		// velocity and fire as sprite controls....
		float y2 = y1;
		// float x2 = camera.getWidth() -
		// this.mOnScreenControlBaseTextureRegion.getWidth() -100.0f;
		// float x3 = camera.getWidth() -
		// this.mOnScreenControlBaseTextureRegion.getWidth() +30.0f;

		float x2 = camera.getWidth()
				- resourcesManager.mOnScreenControlBaseTextureRegion.getWidth()
				- 100.0f;
		float x3 = camera.getWidth()
				- (resourcesManager.hyperSpaceButtonTextureRegionPurple
						.getWidth() + 30f);

		// set up the control to shoot
		redButton = new Sprite(x2, y2,
				resourcesManager.shootButtonTextureRegionRed,
				resourcesManager.activity.getVertexBufferObjectManager()) {
			@Override
			public boolean onAreaTouched(TouchEvent pSceneTouchEvent,
					float pTouchAreaLocalX, float pTouchAreaLocalY) {
				switch (pSceneTouchEvent.getAction()) 
				{
				
				case TouchEvent.ACTION_DOWN:
					// give the radius and angle to
					float radius = 2000.0f;
					float turnAngle = spaceship.getRotation();
					float[] coord = getCoordinatesforStraightProjectile(radius,turnAngle);
					shootBullets(coord[0], coord[1]);
					resourcesManager.shootingSound.play();

					break;
				case TouchEvent.ACTION_UP:
					System.out.println("ok ok ok ok ok stopppppp ");
					break;
				default:
					break;
				}
				return true;
			}

		};

		attachChild(redButton);
		registerTouchArea(redButton);

		// setup the control for thrust.
		blueButton = new Sprite(x3, y2,
				resourcesManager.thrustButtonTextureRegionGreen,
				resourcesManager.activity.getVertexBufferObjectManager()) {
			@Override
			public boolean onAreaTouched(TouchEvent pSceneTouchEvent,
					float pTouchAreaLocalX, float pTouchAreaLocalY) {

				switch (pSceneTouchEvent.getAction()) {
				case TouchEvent.ACTION_DOWN:
					spaceship.animate(100);
					// Apply thrust
					ApplyThrust();

					break;
				case TouchEvent.ACTION_UP:
					spaceship.stopAnimation();

					System.out.println("playerphysics acceleration is "
							+ playerPhysicshandler.getAccelerationX());

					applyBrakes();
					applyBrakes = true;

					// spaceship.registerEntityModifier(new MoveModifier(3,
					// spaceship.getX(), spaceship.getY(),spaceship.getX()-50 ,
					// spaceship.getY()-50, EaseCubicOut.getInstance()));
					System.out.println("stop via gravity ");
					break;
				default:
					break;
				}
				return true;
			}

			// Take the turnangle and radius of the animated sprite and figure
			// out a straight line.
			// based on the angle of the AnimatedSprite
			private float[] getCoordinatesToShoot(float radius, float turnAngle) {
				turnAngle = (float) Math.toRadians(turnAngle);
				// if(turnAngle < 0)
				// {
				// turnAngle = Math.abs(turnAngle) + 180;
				// }
				System.out.println("the turn angle is " + turnAngle);

				float[] arr = new float[2];
				arr[0] = (float) (spaceship.getX() + radius
						* Math.cos(turnAngle));
				arr[1] = (float) (spaceship.getY() - radius
						* Math.sin(turnAngle));
				return arr;
			}

		};
		attachChild(blueButton);
		registerTouchArea(blueButton);

		hyperSpaceButton = new Sprite(x3,
				resourcesManager.mOnScreenControlBaseTextureRegion.getHeight(),
				resourcesManager.hyperSpaceButtonTextureRegionPurple,
				resourcesManager.activity.getVertexBufferObjectManager()) 
		{
			@Override
			public boolean onAreaTouched(TouchEvent pSceneTouchEvent,
					float pTouchAreaLocalX, float pTouchAreaLocalY) 
			{

				switch (pSceneTouchEvent.getAction()) {
				case TouchEvent.ACTION_DOWN:
					System.out.println("hyper space.......");
					// send to hyper space
					SendToHyperSpace();
					System.out.println("");
					// playerPhysicshandler.setVelocity(player_rotation_value_x
					// * 100, player_rotation_value_y * 100);
					break;
				case TouchEvent.ACTION_UP:
					spaceship.stopAnimation();
					// spaceship.registerEntityModifier(new MoveModifier(3,
					// spaceship.getX(), spaceship.getY(), coord[0], coord[1],
					// EaseCubicOut.getInstance()));
					System.out.println("stop via gravity ");
					break;
				default:
					break;
				}
				return true;
			}

			// Take the turnangle and radius of the animated sprite and figure
			// out a straight line.
			private float[] getCoordinatesToShoot(float radius, float turnAngle) {
				turnAngle = (float) Math.toRadians(turnAngle);
				// if(turnAngle < 0)
				// {
				// turnAngle = Math.abs(turnAngle) + 180;
				// }
				System.out.println("the turn angle is " + turnAngle);

				float[] arr = new float[2];
				arr[0] = (float) (spaceship.getX() + radius
						* Math.cos(turnAngle));
				arr[1] = (float) (spaceship.getY() - radius
						* Math.sin(turnAngle));
				return arr;
			}
		};
		attachChild(hyperSpaceButton);
		registerTouchArea(hyperSpaceButton);

	}

	public void ApplyThrust() 
	{
		System.out.println("the player rotation value - x is"
				+ player_rotation_value_x);
		System.out.println("the player rotation value - y is"
				+ player_rotation_value_y);
		// a small tweak
		// the controls would reset so we need to set the thrust to a specific x
		// & y value.
		if (player_rotation_value_x == 0.0f && player_rotation_value_y == 0.0f) {
			player_rotation_value_x = -0.031703413f;
			player_rotation_value_y = 0.9740124f;
		}

		playerPhysicshandler.setVelocity(player_rotation_value_x * 150,
				player_rotation_value_y * 150);
	}

	// on touch of the button send the SpaceShip to Hyperspace .. makig him
	// disappear and reappear randomly in the screen
	public void SendToHyperSpace() {
		Random rand = new Random();
		// detachChild(spaceship);
		// figure out the coordinates for the new warp location.
		int minY = (int) resourcesManager.asteroid_medium_reigon.getHeight();
		int maxY = (int) (camera.getHeight() - resourcesManager.asteroid_medium_reigon
				.getHeight());
		int rangeY = maxY - minY;
		int y = rand.nextInt(rangeY) + minY;

		int minX = (int) resourcesManager.asteroid_medium_reigon.getHeight();
		int maxX = (int) (camera.getHeight() - resourcesManager.asteroid_medium_reigon
				.getHeight());
		int rangeX = maxX - minX;
		int x = rand.nextInt(rangeX) + minX;

		spaceship.setPosition(x, y);
		// attachChild(spaceship);

	}

	// TODO write code for de-accelerating the sprite.
	public void applyBrakes() 
	{
		resourcesManager.activity.runOnUpdateThread(new Runnable() 
		{

			public void run() 
			{
				int timeDecided = 1;
				spaceship.clearEntityModifiers();
				float fromX = spaceship.getX();
				float toX = fromX +  playerPhysicshandler.getVelocityX()/2;

				float fromY = spaceship.getY();
				float toY = fromY + playerPhysicshandler.getVelocityY()/2;

				final IEaseFunction currentEaseFunction = EaseCubicOut.getInstance();
				spaceship.registerEntityModifier(new MoveModifier(timeDecided, fromX, toX, fromY, toY, currentEaseFunction));
				playerPhysicshandler.setVelocity(0, 0);
			}
		});

	}

	// method for travel projectile
	private float[] getCoordinatesforStraightProjectile(float radius,
			float turnAngle) {
		float turnAngleinRadian = (float) Math.toRadians(turnAngle);
		float[] arr = new float[2];
		arr[0] = (float) (bullet_origin_x - radius
				* Math.cos(turnAngleinRadian));
		arr[1] = (float) (bullet_origin_y + radius
				* Math.sin(turnAngleinRadian));
		return arr;
	}

	// load and initialize the bullets and asteroids
	private void initOtherEntities() {
		// initializing bullets and asteroids
		bulletLL = new LinkedList<Sprite>();
		asteroidsLL = new LinkedList<Sprite>();
		bulletsToBeAdded = new LinkedList<Sprite>();
		asteroidsToBeAdded = new LinkedList<Sprite>();
	}

	// method to shoot in a straight line based on the rotation of the
	// Spaceship.
	private void shootBullets(final float pX, final float pY) {
		final Sprite bulletSprite;
		bulletSprite = new Sprite(bullet_origin_x, bullet_origin_y,
				resourcesManager.bullet_region,
				resourcesManager.activity.getVertexBufferObjectManager());
		attachChild(bulletSprite);
		float realMoveDuration = 2.0f;
		System.out.println("the realmoveduration is " + realMoveDuration);

		// defining a move modifier from the projectile's start position to the
		// calculated one
		MoveModifier mod = new MoveModifier(realMoveDuration,
				bulletSprite.getX(), pY, bulletSprite.getY(), pX);
		bulletSprite.registerEntityModifier(mod.deepCopy());
		bulletsToBeAdded.add(bulletSprite);

		// TODO plays a shooting sound
		// shootingSound.play();
	}

	@Override
	public void onBackKeyPressed() {
		SceneManager.getInstance().loadMenuScene(engine);
	}

	@Override
	public SceneType getSceneType() {
		return SceneType.SCENE_GAME;
	}

	@Override
	public void disposeScene() {

		// camera.setHUD(null);
		score = 0;
		resourcesManager.backgroundMusic.stop();

		// TODO code responsible for disposing scene
		// removing all game scene objects.
	}

	public boolean onSceneTouchEvent(Scene pScene, TouchEvent pSceneTouchEvent) 
	{
		System.out.println("pScene.. touch ... ");
		return false;
	}

	private void createGameOverText() {
		gameOverText = new Text(0, 0, resourcesManager.font, "Game Over!", vbom);
	}

	private void displayGameOverText() {
		gameOverText.setPosition(camera.getCenterX(), camera.getCenterY());
		attachChild(gameOverText);
		gameOverDisplayed = true;

		// detach certain things .
		detachChild(spaceship);
		detachChild(redButton);
		detachChild(blueButton);
		detachChild(hyperSpaceButton);
	}

	// creating the Game Display for text
	private void createHUD() {
		gameHUD = new HUD();
		scoreText = new Text(20, 20, resourcesManager.font,
				"Score: 0123456789", new TextOptions(HorizontalAlign.LEFT),
				vbom);
		scoreText.setText("Score: 0");
		gameHUD.attachChild(scoreText);
		camera.setHUD(gameHUD);
	}

	// An Autoparralax background
	private void createBackground() {
		autoParallaxBackground = new AutoParallaxBackground(0, 0, 0, 5);
		ParallaxEntity mParallaxentity = new ParallaxEntity(-4.0f, new Sprite(
				0, 0, resourcesManager.mParallaxLayerBack_reigon,
				resourcesManager.vbom));
		autoParallaxBackground.attachParallaxEntity(mParallaxentity);
		setBackground(autoParallaxBackground);
	}

	private void addToScore(int i) {
		score += i;
		scoreText.setText("Score: " + score);
	}

	// adds a target at a random location and let it move along the x-axis from
	// both direction
	public void addTarget(int astcount) 
	{
		if (astcount % 2 == 0) 
		{
			Random rand = new Random();
			// right side set beyond the camera
			int x = (int) (camera.getWidth() + resourcesManager.asteroid_medium_reigon
					.getWidth());
			int minY = (int) resourcesManager.asteroid_medium_reigon
					.getHeight();
			int maxY = (int) (camera.getHeight() - resourcesManager.asteroid_medium_reigon
					.getHeight());
			int rangeY = maxY - minY;
			int y = rand.nextInt(rangeY) + minY;
			Sprite asteroid = new Sprite(x, y,
					resourcesManager.asteroid_medium_reigon.deepCopy(),
					resourcesManager.vbom);
			attachChild(asteroid);

			int minDuration = 8;
			int maxDuration = 13;
			int rangeDuration = maxDuration - minDuration;
			int actualDuration = rand.nextInt(rangeDuration) + minDuration;

			MoveModifier mod = new MoveModifier(actualDuration,
					asteroid.getX(), -asteroid.getWidth(), asteroid.getY(),
					asteroid.getHeight());
			asteroid.registerEntityModifier(mod.deepCopy());

			asteroidsToBeAdded.add(asteroid);
		} 
		else 
		{
			Random rand = new Random();
			// Top before the camera
			int y = (int) (-resourcesManager.asteroid_medium_reigon.getHeight());

			//			int minX =  (int) resourcesManager.asteroid_medium_reigon
			//					.getWidth();
			int minX =  10;

			int maxX = (int) (camera.getWidth() - resourcesManager.asteroid_medium_reigon
					.getWidth());
			int rangeX = maxX - minX;
			int x = rand.nextInt(rangeX) + minX;

			Sprite asteroid = new Sprite(x, y,
					resourcesManager.asteroid_medium_reigon.deepCopy(),
					resourcesManager.vbom);
			attachChild(asteroid);

			int minDuration = 8;
			int maxDuration = 13;
			int rangeDuration = maxDuration - minDuration;
			int actualDuration = rand.nextInt(rangeDuration) + minDuration;

			MoveModifier mod = new MoveModifier(actualDuration,
					asteroid.getX(),
					asteroid.getX(),
					- asteroid.getHeight(), camera.getHeight() + asteroid.getHeight());
			asteroid.registerEntityModifier(mod.deepCopy());

			asteroidsToBeAdded.add(asteroid);
		}

		if (asteroidsCount == 15) {
			asteroidsCount = 0;
		}
	}

}