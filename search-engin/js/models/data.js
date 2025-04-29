const dummyResults = [
  {
    url: "https://nodejs.org/",
    title: "Node.js® is a JavaScript runtime",
    description:
      "Node.js® is a JavaScript runtime built on Chrome's V8 JavaScript engine.",
  },
  {
    url: "https://expressjs.com/",
    title: "Express - Node.js web application framework",
    description: "Fast, unopinionated, minimalist web framework for Node.js.",
  },
  {
    url: "https://nextjs.org/",
    title: "Next.js by Vercel - The React Framework",
    description:
      "Production grade React applications that scale. The world's leading companies use Next.js.",
  },
  {
    url: "https://redux.js.org/",
    title: "Redux - A Predictable State Container for JS Apps",
    description:
      "Redux helps you write applications that behave consistently, run in different environments, and are easy to test.",
  },
  {
    url: "https://angular.io/",
    title: "Angular - The modern web developer’s platform",
    description:
      "Angular is a platform for building mobile and desktop web applications.",
  },
  {
    url: "https://vuejs.org/",
    title: "Vue.js - The Progressive JavaScript Framework",
    description:
      "Vue.js is an open-source model–view–viewmodel front end JavaScript framework.",
  },
  {
    url: "https://svelte.dev/",
    title: "Svelte • Cybernetically enhanced web apps",
    description:
      "Svelte is a radical new approach to building user interfaces.",
  },
  {
    url: "https://webpack.js.org/",
    title: "webpack",
    description:
      "webpack is a static module bundler for modern JavaScript applications.",
  },
  {
    url: "https://babeljs.io/",
    title: "Babel · The compiler for next generation JavaScript",
    description: "Babel is a JavaScript compiler.",
  },
  {
    url: "https://jestjs.io/",
    title: "Jest · Delightful JavaScript Testing",
    description:
      "Jest is a delightful JavaScript Testing Framework with a focus on simplicity.",
  },
  {
    url: "https://mochajs.org/",
    title: "Mocha - the fun, simple, flexible JavaScript test framework",
    description:
      "Mocha is a feature-rich JavaScript test framework running on Node.js.",
  },
  {
    url: "https://www.python.org/",
    title: "Welcome to Python.org",
    description: "The official home of the Python Programming Language.",
  },
  {
    url: "https://www.djangoproject.com/",
    title: "The Web framework for perfectionists with deadlines | Django",
    description:
      "Django makes it easier to build better web apps more quickly and with less code.",
  },
  {
    url: "https://flask.palletsprojects.com/",
    title: "Flask - Pallets",
    description: "Flask is a lightweight WSGI web application framework.",
  },
  {
    url: "https://fastapi.tiangolo.com/",
    title: "FastAPI",
    description:
      "FastAPI framework, high performance, easy to learn, fast to code, ready for production.",
  },
  {
    url: "https://getbootstrap.com/",
    title: "Bootstrap · The most popular HTML, CSS, and JS library",
    description:
      "Bootstrap is an open source toolkit for developing with HTML, CSS, and JS.",
  },
  {
    url: "https://material-ui.com/",
    title: "MUI: The React component library you always wanted",
    description:
      "MUI offers a comprehensive suite of UI tools to help you ship new features faster.",
  },
  {
    url: "https://ant.design/",
    title: "Ant Design - The world's second most popular React UI framework",
    description: "An enterprise-class UI design language and React UI library.",
  },
  {
    url: "https://chakra-ui.com/",
    title: "Chakra UI",
    description:
      "Simple, Modular and Accessible UI Components for your React Applications.",
  },
  {
    url: "https://storybook.js.org/",
    title: "Storybook: UI component explorer for frontend developers",
    description:
      "Storybook is an open source tool for building UI components and pages in isolation.",
  },
  {
    url: "https://eslint.org/",
    title: "ESLint - Pluggable JavaScript Linter",
    description: "Find and fix problems in your JavaScript code.",
  },
  {
    url: "https://prettier.io/",
    title: "Prettier · Opinionated Code Formatter",
    description: "Prettier is an opinionated code formatter.",
  },
  {
    url: "https://www.postgresql.org/",
    title: "PostgreSQL: The world's most advanced open source database",
    description:
      "PostgreSQL is a powerful, open source object-relational database system.",
  },
  {
    url: "https://www.mongodb.com/",
    title: "MongoDB: The Developer Data Platform",
    description:
      "MongoDB is a source-available cross-platform document-oriented database program.",
  },
  {
    url: "https://redis.io/",
    title: "Redis",
    description:
      "Redis is an open source, in-memory data structure store, used as a database, cache, and message broker.",
  },
  {
    url: "https://www.mysql.com/",
    title: "MySQL :: The world's most popular open source database",
    description:
      "MySQL is an open-source relational database management system.",
  },
  {
    url: "https://www.sqlite.org/",
    title: "SQLite Home Page",
    description:
      "SQLite is a C-language library that implements a small, fast, self-contained SQL database engine.",
  },
  {
    url: "https://www.heroku.com/",
    title: "Heroku | Cloud Application Platform",
    description:
      "Heroku is a platform as a service (PaaS) that enables developers to build, run, and operate applications entirely in the cloud.",
  },
  {
    url: "https://vercel.com/",
    title: "Vercel – Develop. Preview. Ship.",
    description:
      "Vercel is the platform for frontend frameworks and static sites, built to integrate with your headless content, commerce, or database.",
  },
  {
    url: "https://netlify.com/",
    title: "Netlify: Develop & deploy the best web experiences in record time",
    description: "Netlify is a platform for automating modern web projects.",
  },
  {
    url: "https://aws.amazon.com/",
    title: "Amazon Web Services (AWS) - Cloud Computing Services",
    description:
      "AWS offers reliable, scalable, and inexpensive cloud computing services.",
  },
  {
    url: "https://azure.microsoft.com/",
    title: "Microsoft Azure: Cloud Computing Services",
    description:
      "Microsoft Azure is a cloud computing service for building, testing, deploying, and managing applications.",
  },
  {
    url: "https://cloud.google.com/",
    title: "Google Cloud: Cloud Computing Services",
    description:
      "Google Cloud provides flexible infrastructure, end-to-security, modern productivity, and intelligent insights.",
  },
  {
    url: "https://digitalocean.com/",
    title: "DigitalOcean – The developer cloud",
    description:
      "DigitalOcean is a cloud infrastructure provider for developers.",
  },
  {
    url: "https://www.docker.com/",
    title: "Docker: Accelerated Container Application Development",
    description:
      "Docker is a platform designed to help developers build, share, and run modern applications.",
  },
  {
    url: "https://kubernetes.io/",
    title: "Kubernetes",
    description:
      "Kubernetes is an open-source system for automating deployment, scaling, and management of containerized applications.",
  },
  {
    url: "https://www.jenkins.io/",
    title: "Jenkins",
    description:
      "Jenkins is an open source automation server which enables developers around the world to reliably build, test, and deploy their software.",
  },
  {
    url: "https://circleci.com/",
    title: "CircleCI - Continuous Integration and Delivery",
    description:
      "CircleCI is a continuous integration and delivery platform that helps software teams work smarter, faster.",
  },
  {
    url: "https://travis-ci.org/",
    title: "Travis CI - Test and Deploy with Confidence",
    description:
      "Travis CI is a hosted continuous integration service used to build and test software projects hosted at GitHub.",
  },
  {
    url: "https://github.com/features/actions",
    title: "GitHub Actions · Automate your workflow",
    description:
      "GitHub Actions makes it easy to automate all your software workflows.",
  },
  {
    url: "https://about.gitlab.com/",
    title: "GitLab | The DevSecOps Platform",
    description:
      "GitLab is a complete DevOps platform, delivered as a single application.",
  },
  {
    url: "https://bitbucket.org/",
    title: "Bitbucket | Git solution for teams using Jira",
    description:
      "Bitbucket is more than just Git code management. It gives teams one place to plan projects, collaborate on code, test, and deploy.",
  },
  {
    url: "https://www.jetbrains.com/webstorm/",
    title: "WebStorm: The Smartest JavaScript IDE",
    description:
      "WebStorm is a powerful IDE for modern JavaScript development.",
  },
  {
    url: "https://code.visualstudio.com/",
    title: "Visual Studio Code - Code Editing. Redefined",
    description:
      "Visual Studio Code is a free source-code editor made by Microsoft for Windows, Linux and macOS.",
  },
  {
    url: "https://www.sublimetext.com/",
    title:
      "Sublime Text - A sophisticated text editor for code, markup and prose",
    description:
      "Sublime Text is a sophisticated text editor for code, markup and prose.",
  },
  {
    url: "https://atom.io/",
    title: "Atom",
    description: "A hackable text editor for the 21st Century.",
  },
  {
    url: "https://notepad-plus-plus.org/",
    title: "Notepad++ Home",
    description:
      "Notepad++ is a free source code editor and Notepad replacement that supports several languages.",
  },
  {
    url: "https://www.gnu.org/software/emacs/",
    title: "GNU Emacs",
    description:
      "An extensible, customizable, free/libre text editor — and more.",
  },
  {
    url: "https://www.vim.org/",
    title: "Vim - the ubiquitous text editor",
    description:
      "Vim is a highly configurable text editor built to enable efficient text editing.",
  },
  {
    url: "https://www.linux.org/",
    title: "Linux.org",
    description: "The Linux community and resource site.",
  },
  {
    url: "https://ubuntu.com/",
    title: "Ubuntu",
    description:
      "Ubuntu is an open source software platform that runs everywhere from the PC to the server and the cloud.",
  },
  {
    url: "https://www.debian.org/",
    title: "Debian -- The Universal Operating System",
    description: "Debian is a free operating system (OS) for your computer.",
  },
  {
    url: "https://www.archlinux.org/",
    title: "Arch Linux",
    description: "A simple, lightweight distribution.",
  },
  {
    url: "https://www.raspberrypi.org/",
    title: "Raspberry Pi",
    description: "The official website of the Raspberry Pi Foundation.",
  },
  {
    url: "https://www.arduino.cc/",
    title: "Arduino - Home",
    description:
      "Open-source electronic prototyping platform enabling users to create interactive electronic objects.",
  },
  {
    url: "https://www.qt.io/",
    title: "Qt | Cross-platform software development for embedded & desktop",
    description:
      "Qt is the faster, smarter way to create innovative devices, modern UIs & applications for multiple screens.",
  },
  {
    url: "https://www.electronjs.org/",
    title:
      "Electron | Build cross-platform desktop apps with JavaScript, HTML, and CSS.",
    description:
      "Electron enables you to create desktop applications with pure JavaScript by providing a runtime with rich native APIs.",
  },
  {
    url: "https://cordova.apache.org/",
    title: "Apache Cordova",
    description:
      "Apache Cordova enables software programmers to build applications for mobile devices using CSS3, HTML5, and JavaScript.",
  },
  {
    url: "https://ionicframework.com/",
    title: "Ionic: Cross-Platform Mobile App Development",
    description:
      "Ionic is an open source mobile UI toolkit for building high quality, cross-platform native and web app experiences.",
  },
  {
    url: "https://reactnative.dev/",
    title: "React Native · Learn once, write anywhere",
    description:
      "React Native combines the best parts of native development with React.",
  },
  {
    url: "https://flutter.dev/",
    title: "Flutter - Build apps for any screen",
    description:
      "Flutter transforms the app development process. Build, test, and deploy beautiful mobile, web, desktop, and embedded apps from a single codebase.",
  },
  {
    url: "https://developer.mozilla.org/",
    title: "MDN Web Docs",
    description:
      "Resources for developers, by developers. MDN Web Docs is the best resource for web documentation.",
  },
  {
    url: "https://w3schools.com/",
    title: "W3Schools Online Web Tutorials",
    description: "W3Schools is optimized for learning, testing, and training.",
  },
  {
    url: "https://www.freecodecamp.org/",
    title: "freeCodeCamp.org",
    description: "Learn to code for free. Build projects. Earn certifications.",
  },
  {
    url: "https://www.codecademy.com/",
    title: "Learn to Code - for Free | Codecademy",
    description: "Codecademy is the easiest way to learn how to code.",
  },
  {
    url: "https://www.udemy.com/",
    title: "Online Courses - Learn Anything, On Your Schedule | Udemy",
    description:
      "Udemy is an online learning and teaching marketplace with over 213,000 courses.",
  },
  {
    url: "https://www.coursera.org/",
    title: "Coursera | Online Courses & Credentials From Top Educators",
    description:
      "Learn online and earn valuable credentials from top universities like Yale, Michigan, Stanford, and leading companies.",
  },
  {
    url: "https://www.edx.org/",
    title: "edX | Free Online Courses by Harvard, MIT, & more",
    description:
      "Access 2000 free online courses from 140 leading institutions worldwide.",
  },
  {
    url: "https://www.khanacademy.org/",
    title: "Khan Academy | Free Online Courses, Lessons & Practice",
    description:
      "Khan Academy offers practice exercises, instructional videos, and a personalized learning dashboard.",
  },
  {
    url: "https://www.pluralsight.com/",
    title: "Pluralsight | The tech workforce development company",
    description:
      "Pluralsight helps individuals and businesses build technology skills.",
  },
  {
    url: "https://www.hackerrank.com/",
    title: "HackerRank",
    description: "Practice coding, prepare for interviews, and get hired.",
  },
  {
    url: "https://leetcode.com/",
    title:
      "LeetCode - The World's Leading Online Programming Learning Platform",
    description: "Level up your coding skills and quickly land a job.",
  },
  {
    url: "https://www.codewars.com/",
    title: "Codewars: Achieve mastery through coding challenge",
    description:
      "Codewars is where developers achieve code mastery through challenge.",
  },
  {
    url: "https://www.topcoder.com/",
    title: "Topcoder: Delivering Outcomes Through Crowdsourcing",
    description:
      "Topcoder is a crowdsourcing company with an open global community of designers, developers, data scientists, and competitive programmers.",
  },
  {
    url: "https://www.codechef.com/",
    title:
      "CodeChef | Competitive Programming | Participate & Learn | CodeChef",
    description: "CodeChef is a competitive programming community.",
  },
  {
    url: "https://www.hackerearth.com/",
    title: "HackerEarth - Coding Challenges and Developer Jobs",
    description: "HackerEarth is a global hub of 5M+ developers.",
  },
  {
    url: "https://www.stackoverflow.com/",
    title: "Stack Overflow - Where Developers Learn, Share, & Build Careers",
    description:
      "Stack Overflow is the largest, most trusted online community for developers.",
  },
  {
    url: "https://superuser.com/",
    title: "Super User",
    description:
      "Super User is a question and answer site for computer enthusiasts and power users.",
  },
  {
    url: "https://serverfault.com/",
    title: "Server Fault",
    description:
      "Server Fault is a question and answer site for system and network administrators.",
  },
  {
    url: "https://askubuntu.com/",
    title: "Ask Ubuntu",
    description:
      "Ask Ubuntu is a question and answer site for Ubuntu users and developers.",
  },
  {
    url: "https://math.stackexchange.com/",
    title: "Mathematics Stack Exchange",
    description:
      "Q&A for people studying math at any level and professionals in related fields.",
  },
  {
    url: "https://www.geeksforgeeks.org/",
    title: "GeeksforGeeks | A computer science portal for geeks",
    description: "GeeksforGeeks is a computer science portal for geeks.",
  },
  {
    url: "https://www.tutorialspoint.com/",
    title: "Tutorials Point",
    description:
      "Tutorials Point is a leading Ed Tech company striving to provide the best learning material.",
  },
  {
    url: "https://www.programiz.com/",
    title: "Programiz: Learn to Code for Free",
    description:
      "Programiz provides tutorials and examples to learn programming languages.",
  },
  {
    url: "https://www.javatpoint.com/",
    title: "JavaTpoint",
    description:
      "JavaTpoint provides tutorials on Java, Python, Android, and more.",
  },
  {
    url: "https://www.java.com/",
    title: "Java | Oracle",
    description:
      "Java is a programming language and computing platform first released by Sun Microsystems in 1995.",
  },
  {
    url: "https://kotlinlang.org/",
    title: "Kotlin Programming Language",
    description:
      "Kotlin is a modern statically typed programming language used by over 60% of professional Android developers.",
  },
  {
    url: "https://www.rust-lang.org/",
    title: "Rust Programming Language",
    description:
      "A language empowering everyone to build reliable and efficient software.",
  },
  {
    url: "https://go.dev/",
    title: "The Go Programming Language",
    description:
      "Go is an open source programming language that makes it easy to build simple, reliable, and efficient software.",
  },
  {
    url: "https://www.ruby-lang.org/",
    title: "Ruby Programming Language",
    description:
      "A dynamic, open source programming language with a focus on simplicity and productivity.",
  },
  {
    url: "https://www.php.net/",
    title: "PHP: Hypertext Preprocessor",
    description:
      "PHP is a popular general-purpose scripting language that is especially suited to web development.",
  },
  {
    url: "https://laravel.com/",
    title: "Laravel - The PHP Framework For Web Artisans",
    description:
      "Laravel is a web application framework with expressive, elegant syntax.",
  },
  {
    url: "https://symfony.com/",
    title: "Symfony, High Performance PHP Framework for Web Development",
    description:
      "Symfony is a set of reusable PHP components and a PHP framework for web projects.",
  },
  {
    url: "https://spring.io/",
    title: "Spring | Home",
    description:
      "Spring makes Java simple, modern, productive, reactive, cloud-ready.",
  },
  {
    url: "https://dotnet.microsoft.com/",
    title: ".NET | Free. Cross-platform. Open Source.",
    description:
      ".NET is a free, cross-platform, open source developer platform for building many different types of applications.",
  },
  {
    url: "https://www.cplusplus.com/",
    title: "C++ Reference",
    description:
      "C++ is a general-purpose programming language created by Bjarne Stroustrup as an extension of the C programming language.",
  },
  {
    url: "https://www.cprogramming.com/",
    title: "Cprogramming.com",
    description: "Cprogramming.com covers both C and C++ programming.",
  },
  {
    url: "https://www.perl.org/",
    title: "The Perl Programming Language - www.perl.org",
    description: "Perl is a highly capable, feature-rich programming language.",
  },
  {
    url: "https://www.scala-lang.org/",
    title: "Scala Programming Language",
    description:
      "Scala combines object-oriented and functional programming in one concise, high-level language.",
  },
  {
    url: "https://clojure.org/",
    title: "Clojure",
    description:
      "Clojure is a dynamic, general-purpose programming language, combining the approachability and interactive development of a scripting language.",
  },
  {
    url: "https://elixir-lang.org/",
    title: "Elixir",
    description:
      "Elixir is a dynamic, functional language designed for building scalable and maintainable applications.",
  },
  {
    url: "https://www.swift.org/",
    title: "Swift.org - Welcome to Swift.org",
    description:
      "Swift is a powerful and intuitive programming language for macOS, iOS, watchOS, tvOS and beyond.",
  },
  {
    url: "https://developer.apple.com/xcode/",
    title: "Xcode - Apple Developer",
    description:
      "Xcode includes everything developers need to create great applications for Mac, iPhone, iPad, Apple TV, and Apple Watch.",
  },
  {
    url: "https://firebase.google.com/",
    title: "Firebase",
    description: "Firebase helps you build and run successful apps.",
  },
  {
    url: "https://supabase.com/",
    title: "Supabase | The Open Source Firebase Alternative",
    description: "Supabase is an open source Firebase alternative.",
  },
  {
    url: "https://www.elastic.co/",
    title: "Elastic | Search, Observability, and Security",
    description:
      "Elastic is a search company. As the creators of the Elastic Stack, we help everyone find the answers they need.",
  },
  {
    url: "https://www.algolia.com/",
    title: "Algolia: Fast, reliable and secure search APIs",
    description:
      "Algolia empowers builders with the search and recommendation services they need.",
  },
  {
    url: "https://www.cloudflare.com/",
    title: "Cloudflare - The Web Performance & Security Company",
    description: "Cloudflare helps build a better Internet.",
  },
  {
    url: "https://www.nginx.com/",
    title:
      "NGINX | High Performance Load Balancer, Web Server, & Reverse Proxy",
    description:
      "NGINX accelerates content and application delivery, improves security, and facilitates availability and scalability.",
  },
  {
    url: "https://httpd.apache.org/",
    title: "Apache HTTP Server Project",
    description:
      "The Apache HTTP Server Project is an effort to develop and maintain an open-source HTTP server for modern operating systems.",
  },
  {
    url: "https://www.gnu.org/software/bash/",
    title: "Bash",
    description: "Bash is the GNU Project's shell—the Bourne Again SHell.",
  },
  {
    url: "https://powershell.microsoft.com/",
    title: "PowerShell",
    description:
      "PowerShell is a cross-platform task automation solution made up of a command-line shell, a scripting language, and a configuration management framework.",
  },
  {
    url: "https://www.zsh.org/",
    title: "Zsh",
    description:
      "Zsh is a shell designed for interactive use, although it is also a powerful scripting language.",
  },
  {
    url: "https://fishshell.com/",
    title: "fish shell",
    description:
      "fish is a smart and user-friendly command line shell for Linux, macOS, and the rest of the family.",
  },
];
module.exports = { dummyResults };