class AtomicCore {
  constructor(scene,quality){
    this.scene=scene;this.quality=quality;this.group=new THREE.Group();this.isDisposed=false;
    const low=quality==='low',high=quality==='high',seg=low?40:high?88:64;
    const U={uTime:{value:0},uIntensity:{value:1},uPulse:{value:.04},uFlare:{value:0}};
    const noise=[
      'float h(vec3 p){return fract(sin(dot(p,vec3(127.1,311.7,74.7)))*43758.5453);}',
      'float n3(vec3 p){vec3 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(mix(h(i),h(i+vec3(1,0,0)),f.x),mix(h(i+vec3(0,1,0)),h(i+vec3(1,1,0)),f.x),f.y),mix(mix(h(i+vec3(0,0,1)),h(i+vec3(1,0,1)),f.x),mix(h(i+vec3(0,1,1)),h(i+vec3(1,1,1)),f.x),f.y),f.z);}',
      'float fbm(vec3 p){float v=0.0,a=.5;for(int i=0;i<4;i++){v+=a*(n3(p)*2.0-1.0);p*=2.03;a*=.5;}return v;}',
      'float ridge(vec3 p){float v=0.0,a=.55;for(int i=0;i<4;i++){float q=1.0-abs(n3(p)*2.0-1.0);v+=a*q*q;p*=2.1;a*=.48;}return v;}'
    ].join('\n');
    const vert=[
      'uniform float uTime;uniform float uPulse;varying vec3 vP;varying vec3 vN;varying vec3 vW;',
      noise,
      'void main(){vP=position;float a=fbm(position*2.0+vec3(uTime*.28,-uTime*.22,uTime*.2));float b=ridge(position*3.6-vec3(uTime*.4));',
      'vec3 p=position+normal*(a*.12+b*.07+sin(uTime*1.3+position.y*2.0)*uPulse);vec4 w=modelMatrix*vec4(p,1.0);vW=w.xyz;vN=normalize(mat3(modelMatrix)*normal);gl_Position=projectionMatrix*viewMatrix*w;}'
    ].join('\n');
    const frag=[
      'uniform float uTime;uniform float uIntensity;uniform float uFlare;varying vec3 vP;varying vec3 vN;varying vec3 vW;',
      noise,
      'void main(){vec3 V=normalize(cameraPosition-vW);float rim=pow(1.0-max(dot(normalize(vN),V),0.0),2.3);',
      'float base=fbm(vP*2.4+vec3(uTime*.32,-uTime*.25,uTime*.2));float fil=ridge(vP*4.2-vec3(uTime*.45));',
      'vec3 c=mix(vec3(.008,.045,.26),vec3(.02,.31,.9),smoothstep(-.4,.4,base));c=mix(c,vec3(.04,.82,1.0),smoothstep(.28,.76,fil));',
      'c=mix(c,vec3(.9,.99,1.0),smoothstep(.76,.97,fil)*.85);c+=vec3(.05,.8,1.0)*rim*.72+vec3(.9,.99,1.0)*pow(rim,4.0)*.4;',
      'float flash=smoothstep(.8,.98,fil)*uFlare;c+=vec3(.9,.99,1.0)*flash;c*=uIntensity;gl_FragColor=vec4(c,1.0);}'
    ].join('\n');
    const shellFrag=[
      'uniform float uTime;uniform float uIntensity;varying vec3 vP;varying vec3 vN;varying vec3 vW;',noise,
      'void main(){vec3 V=normalize(cameraPosition-vW);float rim=pow(1.0-abs(dot(normalize(vN),V)),1.6);float f=ridge(vP*3.1+vec3(uTime*.2,-uTime*.35,uTime*.27));',
      'vec3 c=mix(vec3(.01,.16,.62),vec3(.06,.82,1.0),smoothstep(.25,.8,f));float a=clamp((rim*.55+f*.25)*uIntensity,0.0,.72);gl_FragColor=vec4(c,a);}'
    ].join('\n');
    const mat=(fs,transparent)=>new THREE.ShaderMaterial({uniforms:U,vertexShader:vert,fragmentShader:fs,transparent:!!transparent,depthWrite:!transparent,blending:transparent?THREE.AdditiveBlending:THREE.NormalBlending,side:transparent?THREE.DoubleSide:THREE.FrontSide});
    this.group.add(new THREE.Mesh(new THREE.IcosahedronGeometry(1.0,low?3:high?5:4),mat(frag,false)));
    this.group.add(new THREE.Mesh(new THREE.IcosahedronGeometry(1.28,low?2:high?4:3),mat(shellFrag,true)));
    this.group.add(new THREE.Mesh(new THREE.IcosahedronGeometry(1.55,low?1:2),mat(shellFrag,true)));
    const rc=5,verts=(seg+1)*2*rc,pos=new Float32Array(verts*3),uv=new Float32Array(verts*2),meta=new Float32Array(verts*2),idx=new Uint16Array(seg*6*rc);let v=0,k=0;
    for(let r=0;r<rc;r++){const seed=(r+1)*1.618,base=1.5+(r%3)*.17,arc=Math.PI*(1.2+(r%2)*.32),start=v;
      for(let i=0;i<=seg;i++){const u=i/seg,t=(u-.5)*arc+seed,rad=base+Math.sin(t*2+seed)*.16,y=Math.sin(t*2.5+seed*1.4)*.34;
        const x=Math.cos(t)*rad,z=Math.sin(t)*rad;const a=r*1.17-1.1,b=r*2.1+.5,c=r*.9-.7;
        for(let s=0;s<2;s++){const sign=s?1:-1,w=.05*Math.pow(Math.sin(u*Math.PI),.7)*sign;const xx=x+w*Math.cos(t),zz=z-w*Math.sin(t);
          const cy=Math.cos(c),sy=Math.sin(c),cx=Math.cos(a),sx=Math.sin(a),cz=Math.cos(b),sz=Math.sin(b);
          let X=xx*cz+zz*sz,Z=-xx*sz+zz*cz,Y=y;let Y2=Y*cx-Z*sx,Z2=Y*sx+Z*cx;let X2=X*cy-Y2*sy,Y3=X*sy+Y2*cy;
          pos[v*3]=X2;pos[v*3+1]=Y3;pos[v*3+2]=Z2;uv[v*2]=u;uv[v*2+1]=s;meta[v*2]=r/rc;meta[v*2+1]=seed;v++;}}
      for(let i=0;i<seg;i++){const a=start+i*2,b=a+1,c=a+2,d=a+3;idx.set([a,c,b,b,c,d],k);k+=6;}}
    const rg=new THREE.BufferGeometry();rg.setAttribute('position',new THREE.BufferAttribute(pos,3));rg.setAttribute('uv',new THREE.BufferAttribute(uv,2));rg.setAttribute('aMeta',new THREE.BufferAttribute(meta,2));rg.setIndex(new THREE.BufferAttribute(idx,1));
    const rm=new THREE.ShaderMaterial({uniforms:U,transparent:true,depthWrite:false,blending:THREE.AdditiveBlending,side:THREE.DoubleSide,
      vertexShader:['attribute vec2 aMeta;uniform float uTime;varying vec2 vUv;varying float vSeed;','void main(){vUv=uv;vSeed=aMeta.y;float ang=uTime*(.35+aMeta.x*.28)*(mod(floor(aMeta.x*5.0),2.0)<.5?1.0:-1.0);float s=sin(ang),c=cos(ang);vec3 p=position+normalize(position)*sin(uv.x*9.0-uTime*2.4+vSeed)*.08;p=vec3(c*p.x+s*p.z,p.y,-s*p.x+c*p.z);gl_Position=projectionMatrix*modelViewMatrix*vec4(p,1.0);}'].join('\n'),
      fragmentShader:['uniform float uTime;varying vec2 vUv;varying float vSeed;','void main(){float pulse=pow(sin(vUv.x*13.0-uTime*3.4+vSeed*4.0)*.5+.5,2.0);float edge=sin(vUv.y*3.14159)*pow(sin(vUv.x*3.14159),.75);vec3 c=mix(vec3(.02,.3,.95),vec3(.08,.9,1.0),pulse);c=mix(c,vec3(.92,.99,1.0),pow(pulse,2.5)*.8);gl_FragColor=vec4(c,edge*(.22+pulse*.62));}'].join('\n')});
    this.group.add(new THREE.Mesh(rg,rm));
    const count=low?18:high?44:30,pg=new THREE.BufferGeometry(),p=new Float32Array(count*3),orb=new Float32Array(count*4),vis=new Float32Array(count*2);
    for(let i=0;i<count;i++){orb.set([1.35+Math.random()*1.5,Math.random()*Math.PI*2,Math.acos(2*Math.random()-1),(.2+Math.random()*.45)*(i%2?1:-1)],i*4);vis.set([12+Math.random()*20,Math.random()*6.28],i*2);}
    pg.setAttribute('position',new THREE.BufferAttribute(p,3));pg.setAttribute('aOrbit',new THREE.BufferAttribute(orb,4));pg.setAttribute('aVisual',new THREE.BufferAttribute(vis,2));
    const pm=new THREE.ShaderMaterial({uniforms:U,transparent:true,depthWrite:false,blending:THREE.AdditiveBlending,
      vertexShader:['attribute vec4 aOrbit;attribute vec2 aVisual;uniform float uTime;varying float vA;','void main(){float th=aOrbit.y+uTime*aOrbit.w,ph=aOrbit.z+sin(uTime*.45+aVisual.y)*.3,r=aOrbit.x+sin(uTime*1.3+aVisual.y)*.1;vec3 p=vec3(r*sin(ph)*cos(th),r*cos(ph),r*sin(ph)*sin(th));vec4 mv=modelViewMatrix*vec4(p,1.0);vA=.25+.75*(sin(uTime*2.5+aVisual.y*2.0)*.5+.5);gl_PointSize=aVisual.x/max(-mv.z,.5);gl_Position=projectionMatrix*mv;}'].join('\n'),
      fragmentShader:['varying float vA;','void main(){vec2 p=gl_PointCoord-.5;float d=length(p)*2.0;if(d>1.0)discard;float g=exp(-d*d*8.5)+pow(1.0-d,2.0)*.5;gl_FragColor=vec4(.08,.85,1.0,g*vA);}'].join('\n')});
    this.group.add(new THREE.Points(pg,pm));this.uniforms=U;this.scene.add(this.group);
  }
  update(dt,time,state,speed){if(this.isDisposed)return;this.uniforms.uTime.value=time*speed;this.uniforms.uIntensity.value=state==='processing'?1.25:state==='speaking'?1.35:1.0;this.uniforms.uPulse.value=state==='listening'?.06:.04;this.uniforms.uFlare.value=.5+.5*Math.sin(time*.7);this.group.rotation.y=time*.12*speed;this.group.rotation.x=Math.sin(time*.22)*.07;}
  setVisibility(v){this.group.visible=!!v;}
  dispose(){if(this.isDisposed)return;this.isDisposed=true;this.scene.remove(this.group);this.group.traverse(o=>{if(o.geometry)o.geometry.dispose();if(o.material){if(Array.isArray(o.material))o.material.forEach(m=>m.dispose());else o.material.dispose();}});}
}